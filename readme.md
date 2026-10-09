# SRM 供应商管理系统

> 企业级 SRM（供应商关系管理）系统，基于 Spring Boot 3.5 + Vue 3 前后端分离架构，覆盖供应商全生命周期管理与采购订单闭环协同，并集成基于 Spring AI + 通义千问（DashScope）的 AI 智能采购 Agent，支持流式对话、询比价分析、图谱智能分析（Neo4j）与 RAG 混合检索（pgvector）。

---

## 项目描述

**SRM 供应商管理系统** 是一套面向企业采购部门的全流程数字化平台，旨在帮助采购团队高效管理供应商资源、追踪采购订单状态，并借助 AI 智能助手完成询比价分析与决策支持。

### 核心能力

- **供应商全生命周期管理**：从供应商注册、资质审核，到准入、合作、冻结、黑名单的完整状态机流转，支持多维度组合筛选（地区、品类、资质等级、状态），帮助企业建立结构化的供应商资源池。
- **采购订单闭环协同**：覆盖"待确认 → 生产中 → 已发货 → 已签收"的订单状态流转，内置 Redisson 分布式锁防重提交机制，保障高并发场景下的数据一致性与幂等性。
- **AI 智能询比价助手**：基于 LangChain + 通义千问大模型的采购 Agent，具备供应商知识库检索（RAG）、历史价格参考、阶梯折扣成本计算、多供应商报价对比排序等能力，通过 SSE 流式输出提供实时对话体验，辅助采购决策。
- **多级缓存体系**：贯穿 Nginx 静态资源缓存 → Redis 业务数据缓存（字典 / 供应商 / 订单状态）→ Redisson 分布式锁 → HikariCP 连接池的四层缓存架构，在保障数据一致性的前提下显著降低数据库压力。
- **安全与鉴权**：JWT 无状态认证 + BCrypt 密码加密 + 登录拦截器 + 前端路由守卫，确保系统访问安全可控。

### 适用场景

| 场景             | 说明                                  |
| ---------------- | ------------------------------------- |
| 企业供应商管理   | 集中管理供应商信息、资质、合作状态    |
| 采购订单跟踪     | 实时追踪订单从下发到签收的全过程      |
| AI 辅助采购决策  | 利用历史订单数据和 LLM 进行智能询比价 |
| 基础数据字典维护 | 统一管理地区、品类、资质等级等字典项  |

---

## 技术栈总览

| 层级         | 技术                        | 版本            |
| ------------ | --------------------------- | --------------- |
| **语言**     | Java / JavaScript           | 21 / ES2022+    |
| **后端框架** | Spring Boot                 | 3.5.0           |
| **前端框架** | Vue 3 + Vue Router          | 3.5.x / 4.5.x   |
| **构建工具** | Maven / Vite                | — / 6.x         |
| **持久层**   | MyBatis-Plus + PostgreSQL   | 3.5.10 / 16.x   |
| **缓存**     | Redis (Lettuce) + Redisson  | 7.x / 3.45.0    |
| **安全**     | JWT (jjwt) + BCrypt         | 0.12.6 / 0.10.2 |
| **AI 框架**  | Spring AI（OpenAI 协议）    | 1.0.0           |
| **向量库**   | pgvector                    | 0.7.x           |
| **图数据库** | Neo4j                       | 5.x             |
| **API 文档** | Knife4j (OpenAPI 3)         | 4.5.0           |
| **工具库**   | Hutool + Lombok             | 5.8.35 / latest |
| **反向代理** | Nginx                       | 1.30.x          |
| **连接池**   | HikariCP (Spring Boot 内置) | —               |

---

## 多级缓存体系

整个系统的缓存架构贯穿 **客户端 → 反向代理 → 应用层 → 数据库连接池** 四个层级，每一层解决不同维度的问题：

```
┌──────────────────────────────────────────────────────┐
│  L0  浏览器 / Nginx 静态资源                          │
│       Vue build 产物 (js/css 带 hash)                  │
│       Cache-Control: public, immutable, 7d            │
│       Gzip level 6                                    │
├──────────────────────────────────────────────────────┤
│  L1  Redis 业务缓存 (Cache-Aside)                     │
│       ┌──────────────┬─────────────┬───────────────┐  │
│       │ Dict (Hash)  │ Supplier    │ Order Status  │  │
│       │ TTL 24h      │ TTL 1h      │ TTL 30min     │  │
│       │ 准静态数据    │ 活跃供应商   │ 状态快照      │  │
│       └──────────────┴─────────────┴───────────────┘  │
├──────────────────────────────────────────────────────┤
│  L2  Redisson 分布式锁 (并发控制)                      │
│       TryLock → Watch Dog 自动续期 → 业务完成释放      │
│       防重提交 / 幂等保障                              │
├──────────────────────────────────────────────────────┤
│  L3  HikariCP 连接池                                  │
│       min-idle=5  max-pool=20                         │
│       连接复用，减少 MySQL 握手开销                     │
└──────────────────────────────────────────────────────┘
```

### L0 — Nginx 静态资源缓存

Vue 前端 `build` 后产物带 content-hash 文件名（如 `index-a1b2c3.js`），内容不变则 URL 不变，因此可设置长期强缓存：

```nginx
location ~* \.(js|css|png|jpg|gif|svg|ico|woff|woff2)$ {
    expires    7d;
    add_header Cache-Control "public, immutable";
}
```

同时开启 Gzip（level 6），对 `text/plain, text/css, application/javascript, application/json` 等 MIME 类型压缩传输。

### L1 — Redis 业务数据缓存

采用 **Cache-Aside** 模式（旁路缓存）：读时先查 Redis，miss 则查 DB 并回写；写时先更新 DB，再删除/更新对应缓存。

三种数据类型采用三种不同的缓存策略：

#### 1. 字典数据 — Redis Hash · 24h TTL

字典是典型的"准静态数据"（地区列表、资质等级、状态枚举），变更频率极低，适合长期缓存。

- **Key**: `srm:dict:type:{dictType}`
- **结构**: Hash，field = id，value = JSON
- **策略**: 按 `dictType` 整组缓存，命中直接返回全量 List
- **失效**: 增/删/改任意一条 → 整组 Hash 删除，下次查询自动重建
- **dictType 变更处理**: 同时删除旧类型和新类型两个 key

```java
// DictItemServiceImpl.listByType()
// 1. 查 Redis Hash  →  命中返回
// 2. Miss → DB 查全量  →  逐条 HSET 回写  →  设 24h 过期
```

#### 2. 供应商详情 — Redis String(JSON) · 1h TTL

供应商查询是最高频的读操作（列表页 + 详情页 + 订单关联），且仅对"已准入/合作中"的供应商做缓存，避免黑名单/冻结数据占用内存。

- **Key**: `srm:supplier:info:{id}`
- **结构**: String (JSON)
- **准入条件**: `status == 2 (已准入) || status == 3 (合作中)` 才写入缓存
- **策略**: Cache-Aside，getById 优先查 Redis，miss 查 DB 回写
- **失效**: updateById / removeById → 直接 delete key

```java
// SupplierServiceImpl.getById()
// 1. GET srm:supplier:info:{id}  →  命中返回
// 2. Miss → DB 查  →  if (status in [2,3]) SETEX 1h
```

#### 3. 订单状态 — Redis String(JSON) · 30min TTL

订单完整数据由 DB 承载（复杂条件分页），Redis 仅缓存"状态快照"（id + status），用于快速状态校验和流转判断。

- **Key**: `srm:order:status:{id}`
- **结构**: String (JSON)，仅存 `{id, status}` 两个字段
- **策略**: 写穿透（Write-Through），save/updateById 成功即同步更新缓存
- **失效**: removeById → 直接 delete key

```java
// OrderServiceImpl.save() / updateById()
// DB 写入成功 → cacheStatus() → SET srm:order:status:{id} 30min
```

### L2 — Redisson 分布式锁（并发控制，非缓存）

虽然本质是并发控制，但在逻辑上与缓存层紧密配合——将"创建中的订单"暂时锁定，防止重复数据穿透到底层 DB：

```
用户请求创建订单
  → tryLock("srm:lock:order:create:{userId}", wait=3s, lease=5s)
     ├─ 成功 → 执行业务 → 写DB → 写Redis状态缓存 → unlock
     └─ 失败 → 返回 429 (疑似重复提交)
```

- **Watch Dog 机制**: 业务执行超 lease time 时，Redisson 自动续期，不会误释放
- **安全释放**: `finally` 块中 `isHeldByCurrentThread()` 判断后才 unlock，避免跨线程释放

### L3 — HikariCP 连接池

```yaml
hikari:
  minimum-idle: 5       # 常驻连接，消除冷启动延迟
  maximum-pool-size: 20 # 峰值并发上限
  idle-timeout: 300s    # 空闲超时回收
  max-lifetime: 1200s   # 连接最大存活时间
```

池化的核心价值是**复用数据库连接**，避免每次请求都经历 TCP 握手 + MySQL 认证的昂贵开销。

---

## 缓存失效策略总结

| 数据     | 结构   | TTL   | 读策略              | 写策略           | 缓存粒度    |
| -------- | ------ | ----- | ------------------- | ---------------- | ----------- |
| 字典     | Hash   | 24h   | Miss 回写           | 先更 DB → 删整组 | 按 dictType |
| 供应商   | String | 1h    | Miss 回写（仅活跃） | 先更 DB → 删 key | 按 id       |
| 订单状态 | String | 30min | —                   | 写穿透同步       | 按 id       |

统一原则：**先更 DB，再动缓存**，避免缓存成功但 DB 失败导致的数据不一致。

---

## 核心功能模块

### 1. 供应商生命周期管理

完整状态机流转：`注册 → 待审核 → 已准入 → 合作中 → 冻结/黑名单`

- 多维度组合查询（地区、品类、资质等级、状态）
- 仅活跃供应商（已准入/合作中）写入 Redis 缓存

### 2. 采购订单协同

严格状态流转校验：`待确认 → 生产中 → 已发货 → 已签收`

- **防重提交**: Redisson 分布式锁，按 userId 粒度串行化
- **幂等保障**: 锁获取失败返回 429，避免重复创建
- **状态缓存**: 写穿透同步订单状态快照到 Redis

### 3. 字典与基础数据

- Redis Hash 长期缓存（24h），减少 MySQL 查询压力
- 变更时整组失效重建，保证一致性

### 4. 用户认证与鉴权

- JWT 无状态认证（jjwt 0.12.6）
- BCrypt 密码加密（at.favre.lib）
- 登录拦截器 + 前端路由守卫

### 5. AI 智能采购 Agent（Spring AI 内嵌）

Java 后端内嵌 Spring AI（OpenAI 协议兼容 DashScope 通义千问），直接调用 Java 业务服务作为工具，替代早期 Python Agent 透传。提供 4 组工具：

| 工具组               | 功能                                             |
| -------------------- | ------------------------------------------------ |
| `SupplierTools`      | 供应商检索、关联风险穿透                          |
| `PriceTools`         | 物料历史价格、采购成本计算                        |
| `StatusFlowTools`    | 供应商 / 订单状态流转规则                         |
| `GraphAnalysisTools` | 集中度风险、替代供应商、综合评分、采购员画像      |

- 支持 SSE 流式输出，前端实时渲染对话
- 对话记忆：Redis 短期窗口（最近 N 条）+ PostgreSQL `srm_agent_message` 长期记忆，按 session 隔离
- 通过 `defaultTools` 自动注册 `@Tool` 注解方法，由 LLM 决策调用工具

### 6. 图谱智能分析（Neo4j）

基于 Neo4j 存储实体关系（Supplier / Material / Cert / Region 节点 + SUPPLIES / SUBSIDIARY_OF / CERTIFIED_WITH 等关系），业务库通过 `Neo4jSyncService` 同步到图谱。

- 供应商综合评分（价格 35% / 质量 25% / 交期 20% / 服务 10% / 风险 10%）
- 供应链集中度风险（HHI 赫芬达尔指数）
- 关联风险穿透（股权/股东/高管多层穿透）
- 替代供应商发现、采购员画像、资质到期预警

### 7. RAG 混合检索（pgvector + Neo4j）

三层存储架构：

| 层               | 存储            | 内容                               |
| ---------------- | --------------- | ---------------------------------- |
| PostgreSQL 业务库 | `rag_document`  | 文档主数据（id/title/content/source/metadata） |
| pgvector         | `vector_store`  | 只存 doc_id + embedding            |
| Neo4j            | 节点 + 关系     | 供应商/物料/资质/组织关系          |

- 写入：`RagDocumentService.add` → 先落主数据，再写向量索引
- 检索：`HybridRagService.retrieve` → 向量召回 + Neo4j 结构化过滤（仅保留「已准入/合作中」且无过期资质）

### 8. RAG 评测（召回率 / 忠实度）

`POST /api/rag/eval/run?k=5` 触发，加载 `eval/golden-dataset.yml` 黄金数据集，逐条评测并落库。

- 检索指标：Recall@K / Precision@K / MRR / NDCG@K（`RagMetricsCalculator`）
- 生成指标：答案准确率（关键词命中）+ 忠实度（`LlmJudgeService` LLM 裁判）
- 结果落库：`srm_rag_eval_run`（批次聚合）+ `srm_rag_eval_result`（用例明细）

### 9. 知识库管理（前端 + 管理员权限）

- 前端「知识库」页面：单条 / 批量导入文档、检索验证
- 管理员权限：`is_admin=1` 才显示页面；后端 `/api/rag/**` 由 `LoginInterceptor` 校验 `isAdmin`，非管理员返回 403

---

## 已知不足与后续规划

### 1. RAG 评测同步执行（性能瓶颈）

`RagEvalRunner.run` 同步遍历黄金数据集，每条用例串行执行：向量检索 + Neo4j 过滤 + 2 次 LLM 调用（答案生成 + 忠实度裁判）。数据量大时非常慢，且会阻塞 HTTP 请求线程。

**状态**：✅ 已实现（RabbitMQ 异步）—— 提交任务 → MQ → `RagEvalListener` 后台评测 → 回写 `srm_rag_eval_run.status`（PENDING/RUNNING/COMPLETED/FAILED）。

### 2. 知识库文档写入同步

`POST /api/rag/documents` 同步调用 embedding API 逐条向量化，大批量导入慢。

**状态**：✅ 已实现（RabbitMQ 异步）—— 文档写入走 `RagDocumentListener` 后台向量化。

### 3. 供应商评分维度不完整

`SupplierScoreService` 的质量分用「信用等级」代理、服务分固定 80，尚未接入真实数据（检验合格率、退货率、准时交货率、服务响应）。

**规划**：接入真实业务数据源后替换代理指标。

### 4. pgvector 与业务库同库

`srm.pgvector.url` 默认指向业务库 `SRM`，未按原计划拆分独立 `srm_vector` 库。

**规划**：拆库，或明确同库策略。

### 5. 忠实度评测依赖 LLM 裁判

忠实度由 LLM 打分，有 token 成本、延迟和随机性，结果可能不稳定。

**规划**：引入更确定的推理一致性 / n-gram 校验作为补充指标。

### 6. 文档主数据 upsert 非原子

`RagDocumentService.add` 用 selectById + insert/updateById 两步，非原子操作。

**规划**：改用 `ON CONFLICT DO UPDATE` 原生 upsert。

### 7. 全局异常处理不精细

`GlobalExceptionHandler` 把 404（NoResourceFoundException）等兜底成 500「系统繁忙」，有误导性。

**规划**：补 404 / 400 等细分处理。

### 8. 资质续期直接写 Neo4j（资质数据无业务库落点）

资质证书（Cert）数据只存在 Neo4j（`graph/seed.cypher` 手动 `MERGE`），业务库 pg 无 cert 表（`srm_supplier` 仅 `qualification_level` 等级字段）。`CertRenewalWorkflowService.autoReviewCert` 直接调用 `Neo4jSupplierRepository.updateCertExpiry` 改 Neo4j 的 Cert 节点，把 Neo4j 当成业务库读写，与「Neo4j 手动同步、业务库 pg 为准」的架构不一致；且 `Neo4jSyncService` 只同步 supplier/user/order，不含 cert。

**状态**：✅ 已实现 —— 新增 `srm_supplier_cert` 表作为权威源，`autoReviewCert` 先写 PG 再经 `Neo4jSyncService.syncCert()` 同步到图谱；前端供应商工作台新增「资质」管理入口。

---

## 架构与代码规范

- **分层架构**: Controller → Service → Mapper，职责清晰
- **DTO/VO 隔离**: DTO 接收参数 + 校验，VO 返回数据 + 字段裁剪，Entity 映射数据库
- **逻辑删除**: 全系统 `is_deleted` 字段，MyBatis-Plus `@TableLogic` 自动拼接条件
- **自动填充**: `createTime / updateTime / createBy / updateBy / isDeleted` 由 `MetaObjectHandler` 统一处理
- **主键策略**: MyBatis-Plus `ASSIGN_ID`（雪花算法）
- **全表更新防护**: `BlockAttackInnerInterceptor` 阻止不带 WHERE 的 UPDATE/DELETE

---

## 项目结构

```
SRM/
├── sql/                          # 数据库脚本
│   ├── init.sql                  # 建表 + 初始字典 + 默认管理员
│   ├── test_data.sql             # 测试数据
│   ├── test_data_extra.sql       # 补充测试数据
│   ├── pgvector.sql              # pgvector 向量索引表
│   ├── rag_document.sql          # RAG 文档主数据表
│   └── rag_eval.sql              # RAG 评测结果表
├── src/main/java/com/srm/
│   ├── SrmApplication.java       # 启动类
│   ├── common/                   # 公共组件
│   │   ├── BaseEntity.java       # 实体基类 (id/createTime/逻辑删除)
│   │   ├── Result.java           # 统一响应体
│   │   ├── PageResult.java       # 分页响应
│   │   ├── CacheConstants.java   # 缓存 Key 前缀与 TTL 常量
│   │   ├── exception/            # 全局异常处理
│   │   └── util/                 # 工具类 (JwtUtil)
│   ├── config/                   # 配置类
│   │   ├── MyBatisPlusConfig.java
│   │   ├── RedisConfig.java      # Redisson 手动配置
│   │   ├── SwaggerConfig.java
│   │   ├── WebMvcConfig.java
│   │   └── interceptor/          # 登录拦截器
│   └── modules/
│       ├── order/                # 采购订单模块
│       │   ├── controller/
│       │   ├── dto/              # 入参对象
│       │   ├── entity/
│       │   ├── mapper/
│       │   ├── service/
│       │   └── vo/               # 返回对象
│       ├── supplier/             # 供应商模块
│       ├── system/               # 字典管理模块
│       ├── user/                 # 用户认证模块
│       ├── agent/                # AI Agent 模块（Spring AI + 工具）
│       │   ├── controller/       # AgentController（SSE 流式对话）
│       │   ├── service/          # AgentService / ChatHistoryService
│       │   └── tools/            # SupplierTools / PriceTools / GraphAnalysisTools 等
│       ├── graph/                # 图谱智能分析（Neo4j）
│       │   ├── controller/       # GraphIntelligenceController
│       │   ├── repository/       # GraphRepository / Neo4jSupplierRepository
│       │   └── service/          # GraphIntelligenceService / SupplierScoreService
│       ├── rag/                  # RAG 混合检索 + 评测
│       │   ├── controller/       # RagController / RagEvalController
│       │   ├── service/          # RagDocumentService / HybridRagService / PgVectorStoreService
│       │   └── eval/             # RagEvalRunner / RagMetricsCalculator / LlmJudgeService
│       ├── rfq/                  # 询价(RFQ)工作流
│       ├── cert/                 # 资质(cert)续期
│       └── notification/         # 通知(notification)
├── src/main/resources/
│   ├── application.yml           # 主配置
│   ├── eval/                     # RAG 评测黄金数据集 (golden-dataset.yml)
│   └── mapper/                   # MyBatis XML 映射
├── frontend/                     # Vue 3 前端
│   └── src/
│       ├── api/                  # Axios 接口封装
│       ├── auth/                 # 会话管理
│       ├── router/               # Vue Router (含路由守卫)
│       ├── components/           # 通用组件 (Pagination)
│       ├── views/                # 页面组件
│       │   ├── Login.vue
│       │   ├── SupplierWorkbench.vue
│       │   ├── OrderWorkbench.vue
│       │   ├── AgentChat.vue
│       │   ├── AnalyticsDashboard.vue
│       │   └── KnowledgeBase.vue
│       ├── styles/               # 全局样式
│       ├── App.vue
│       └── main.js
├── nginx.conf                    # Nginx 配置 (Gzip + 静态缓存 + SSE)
└── pom.xml                       # Maven 依赖管理
```
