# Agent 接口 401 / 422 / 405 错误修复记录

**日期:** 2026-05-31  
**分支:** main  
**影响接口:** `POST /api/agent/chat`, `POST /api/agent/chat/stream`

---

## 问题 1: 401 Unauthorized

### 现象

```
POST /api/agent/chat/stream → 401 Unauthorized
```

### 根因

`WebMvcConfig.java` 中 `LoginInterceptor` 拦截所有 `/**` 路径，`/api/agent/**` 未加入排除列表。

### 修复

**文件:** `src/main/java/com/srm/config/WebMvcConfig.java:20`

排除列表新增 `/api/agent/**`：

```java
.excludePathPatterns(
        "/api/auth/login",
        "/api/agent/**",    // ← 新增
        ...
);
```

---

## 问题 2: 422 Unprocessable Entity

### 现象

```
Python 后台日志: "POST /api/agent/chat/stream HTTP/1.1" 422 Unprocessable Entity
                  "WARNING: Invalid HTTP request received."
前端显示: "AI 助手未生成回复"
```

直接 curl 调用 Python 8000 端口正常，经 Java 代理转发才报 422。

### 根因

`RestClient.exchange()` 存在两层问题：

1. `RestClient.body(String)` 使用 `StringHttpMessageConverter`，当 Content-Type 无 charset 时默认使用 **ISO-8859-1** 编码
2. `exchange()` 回调机制下，请求体与响应的流式读取存在兼容性问题

导致中文编码损坏，Python 端收到非法 HTTP 请求体，h11 协议解析失败。

### 修复

**文件:** `src/main/java/com/srm/modules/agent/controller/AgentProxyController.java`

`proxyStream` 改用 `java.net.http.HttpClient`，显式 UTF-8 编码：

```java
HttpRequest req = HttpRequest.newBuilder()
    .uri(URI.create(agentBaseUrl + path))
    .header("Content-Type", "application/json;charset=UTF-8")
    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
    .build();
HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
```

`proxyJson` 同步显式指定 `charset=UTF-8`。

Controller 改为接收 `String rawBody` 避免 Jackson 重新序列化。

---

## 问题 3: 405 Method Not Allowed（ERROR 日志噪音）

### 现象

```
ERROR c.s.c.exception.GlobalExceptionHandler: 系统异常:
HttpRequestMethodNotSupportedException: Request method 'GET' is not supported
```

### 修复

**文件:** `src/main/java/com/srm/common/exception/GlobalExceptionHandler.java`

新增 `HttpRequestMethodNotSupportedException` 专门处理，降为 WARN 级别。

---

## 问题 4: 前端读取 Token 用了错误的 Storage

### 现象

每次进入 /agent 页面需要刷新才能正常显示。

### 根因

`agentApi.js` 中 `sendMessageStream` 使用 `localStorage.getItem('token')` 读取 token，但登录成功后 token 存储在 **sessionStorage** 的 `srm_token` 键下。两端不一致导致请求头中的 Authorization 始终为空。

### 修复

**文件:** `frontend/src/api/agentApi.js`

```javascript
// Before
const token = localStorage.getItem('token') || ''

// After
import { getToken } from '@/auth/session'
const token = getToken() || ''
```

---

## 改动文件清单

| 文件 | 改动 |
|---|---|
| `src/main/java/com/srm/config/WebMvcConfig.java` | `/api/agent/**` 加入拦截器排除列表 |
| `src/main/java/com/srm/modules/agent/controller/AgentProxyController.java` | `proxyStream` 改用 HttpClient + UTF-8；接收原始 String 透传 |
| `src/main/java/com/srm/common/exception/GlobalExceptionHandler.java` | 新增 `HttpRequestMethodNotSupportedException` 处理 |
| `frontend/src/api/agentApi.js` | 修正 token 读取从 `localStorage` → `getToken()` (sessionStorage) |
