package com.srm.modules.agent.controller;

import cn.hutool.json.JSONUtil;
import com.srm.common.Result;
import com.srm.modules.agent.dto.AgentChatDTO;
import com.srm.modules.agent.dto.ChatMessage;
import com.srm.modules.agent.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 内生智能采购助手（Spring AI 内嵌），替代原 Python Agent HTTP 透传。
 * SSE 协议与前端保持一致：data: {"content": "..."} / data: {"done": true}
 */
@Slf4j
@Tag(name = "AI智能采购助手")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Operation(summary = "智能采购对话（非流式）")
    @PostMapping("/chat")
    public Result<String> chat(@RequestBody AgentChatDTO request) {
        return Result.ok(agentService.chat(request.getQuery(), request.getSessionId()));
    }

    @Operation(summary = "智能采购对话（SSE流式）")
    @PostMapping("/chat/stream")
    public void chatStream(@RequestBody AgentChatDTO request, HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        ServletOutputStream out = response.getOutputStream();
        try {
            for (String chunk : agentService.stream(request.getQuery(), request.getSessionId()).toIterable()) {
                out.write(("data: " + sseContent(chunk) + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            out.write("data: {\"done\": true}\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            log.error("Agent stream failed", e);
            writeError(out);
        }
    }

    @Operation(summary = "获取对话历史")
    @GetMapping("/history/{sessionId}")
    public Result<List<ChatMessage>> history(@PathVariable String sessionId) {
        return Result.ok(agentService.history(sessionId));
    }

    @Operation(summary = "清空对话历史")
    @DeleteMapping("/history/{sessionId}")
    public Result<Void> clearHistory(@PathVariable String sessionId) {
        agentService.clearHistory(sessionId);
        return Result.ok();
    }

    private String sseContent(String chunk) {
        return JSONUtil.toJsonStr(Map.of("content", chunk));
    }

    private void writeError(ServletOutputStream out) {
        try {
            out.write(("data: {\"content\": \"[错误] AI 服务暂时不可用，请稍后重试\"}\n\n").getBytes(StandardCharsets.UTF_8));
            out.write("data: {\"done\": true}\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // 连接已断开，忽略
        }
    }
}
