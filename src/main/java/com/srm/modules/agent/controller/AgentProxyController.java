package com.srm.modules.agent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Tag(name = "AI智能采购助手")
@RestController
@RequestMapping("/api/agent")
public class AgentProxyController {

    private static final Logger log = LoggerFactory.getLogger(AgentProxyController.class);

    private final RestClient restClient;
    private final String agentBaseUrl;
    private final HttpClient httpClient;

    public AgentProxyController(RestClient.Builder builder,
                                 @Value("${srm.agent.base-url:http://localhost:8000}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.agentBaseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        log.info("AgentProxyController initialized, agent base-url: {}", baseUrl);
    }

    @Operation(summary = "智能采购对话（非流式）")
    @PostMapping("/chat")
    public void chat(@RequestBody String rawBody, HttpServletResponse response) throws IOException {
        log.debug("Forwarding chat request");
        proxyJson("/api/agent/chat", rawBody, response);
    }

    @Operation(summary = "智能采购对话（SSE流式）")
    @PostMapping("/chat/stream")
    public void chatStream(@RequestBody String rawBody,
                            HttpServletResponse response) throws IOException {
        log.debug("Forwarding stream chat request");
        proxyStream("/api/agent/chat/stream", rawBody, response);
    }

    @Operation(summary = "获取对话历史")
    @GetMapping("/history/{sessionId}")
    public void getHistory(@PathVariable String sessionId, HttpServletResponse response) throws IOException {
        log.debug("Forwarding history request, sessionId={}", sessionId);
        proxyGet("/api/agent/history/" + sessionId, response);
    }

    @Operation(summary = "清空对话历史")
    @DeleteMapping("/history/{sessionId}")
    public void deleteHistory(@PathVariable String sessionId, HttpServletResponse response) throws IOException {
        log.debug("Forwarding delete history request, sessionId={}", sessionId);
        proxyDelete("/api/agent/history/" + sessionId, response);
    }

    // ---- 代理方法：直接透传，绕过 Spring Jackson 序列化 ----

    private void proxyJson(String path, String body, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter w = response.getWriter();
        try {
            String result = restClient.post().uri(path)
                    .contentType(MediaType.valueOf("application/json;charset=UTF-8")).body(body)
                    .retrieve().body(String.class);
            w.write(result != null ? result : "{}");
        } catch (Exception e) {
            log.error("Agent JSON proxy failed for {}: {}", path, e.getMessage());
            response.setStatus(502);
            w.write("{\"code\":502,\"message\":\"Agent服务不可用\",\"data\":null}");
        }
    }

    private void proxyGet(String path, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        PrintWriter w = response.getWriter();
        try {
            String result = restClient.get().uri(path).retrieve().body(String.class);
            w.write(result != null ? result : "{}");
        } catch (Exception e) {
            log.error("Agent GET proxy failed for {}: {}", path, e.getMessage());
            response.setStatus(502);
            w.write("{\"code\":502,\"message\":\"Agent服务不可用\",\"data\":null}");
        }
    }

    private void proxyDelete(String path, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        PrintWriter w = response.getWriter();
        try {
            String result = restClient.delete().uri(path).retrieve().body(String.class);
            w.write(result != null ? result : "{}");
        } catch (Exception e) {
            log.error("Agent DELETE proxy failed for {}: {}", path, e.getMessage());
            response.setStatus(502);
            w.write("{\"code\":502,\"message\":\"Agent服务不可用\",\"data\":null}");
        }
    }

    private void proxyStream(String path, String body, HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        ServletOutputStream out = response.getOutputStream();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(agentBaseUrl + path))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());

            // 如果 Python 返回非 200，读取错误体并写到 SSE 流
            if (resp.statusCode() != 200) {
                String errBody;
                try (InputStream errStream = resp.body()) {
                    errBody = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
                }
                log.warn("Agent stream returned {}: {}", resp.statusCode(), errBody);
                String errJson = String.format("{\"content\":\"[错误] Agent返回 %d，请稍后重试\"}",
                        resp.statusCode());
                out.write(("data: " + errJson + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.write("data: {\"done\": true}\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                return;
            }

            try (InputStream is = resp.body()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            }
        } catch (Exception e) {
            log.error("Agent stream proxy failed", e);
            String err = "{\"content\":\"[错误] Agent服务暂时不可用，请稍后重试\"}";
            out.write(("data: " + err + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.write("data: {\"done\": true}\n\n".getBytes(StandardCharsets.UTF_8));
        }
        out.flush();
    }
}
