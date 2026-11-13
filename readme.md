# SRM 供应商管理系统

> 个人项目（求职作品）：基于 Spring Boot 3.5 + Vue 3 的供应商关系管理系统，集成 AI 智能采购 Agent、RAG 混合检索（pgvector）与供应链知识图谱（Neo4j）。

## 简介

模拟企业采购场景，覆盖供应商全生命周期、采购订单闭环，并借助 AI 助手完成询比价分析与决策支持。

## 核心亮点

- **AI 智能采购 Agent**：Spring AI 内嵌，`@Tool` 工具调用 + SSE 流式对话，PG/Redis 分层会话记忆。
- **RAG 混合检索**：pgvector 向量召回 + Neo4j 结构化过滤，配套可量化评测体系（Recall/Precision/MRR/NDCG/忠实度，RabbitMQ 异步）。
- **供应链知识图谱**：HHI 集中度、关联风险穿透、替代供应商、综合评分、采购员画像。
- **资质自动续期工作流**：定时扫描 → 通知 → AI 审核 → PG 权威源 → 同步图谱。
- **多级缓存**：Nginx 静态缓存 → Redis 业务缓存 → Redisson 分布式锁 → HikariCP 连接池。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.5 · Java 21 · MyBatis-Plus |
| 前端 | Vue 3 + Vite |
| 数据库 | PostgreSQL + pgvector · Neo4j 5 |
| 缓存/锁 | Redis 7 + Redisson |
| 消息队列 | RabbitMQ |
| AI | Spring AI 1.0（通义千问 DashScope） |
| 安全 | JWT + BCrypt |

## 核心功能

- **供应商生命周期**：注册 → 待审核 → 已准入 → 合作中 → 冻结/黑名单，多维度组合筛选。
- **采购订单闭环**：待确认 → 生产中 → 已发货 → 已签收，Redisson 分布式锁防重提交。
- **AI 询比价助手**：供应商检索、历史价格、阶梯折扣、多供应商比价，SSE 流式输出。
- **图谱智能分析**：供应链集中度、关联风险穿透、替代供应商发现、采购员画像。
- **RAG 混合检索**：三层存储（PG 主数据 + pgvector 向量 + Neo4j 关系），配套评测体系。
- **用户认证**：JWT 无状态认证 + BCrypt 加密 + 登录拦截器 + 前端路由守卫。

## 架构要点

- **分层**：Controller → Service → Mapper；DTO/VO/Entity 分离。
- **缓存**：Cache-Aside，先更 DB 再动缓存；字典 Hash / 供应商 String / 订单状态 String 三套策略。
- **一致性**：PG 为业务权威源，Neo4j 由 `Neo4jSyncService` 同步（最终一致）。
- **公共能力**：逻辑删除、雪花主键、审计字段自动填充、全表更新防护。

## 已知不足

| 不足 | 改进方向 |
| --- | --- |
| 检索只做纯向量，无关键词/BM25 | Hybrid Search（RRF 融合） |
| 文本切块无 overlap | sliding window + overlap |
| 结构化过滤用字符串 contains 匹配 | NER / 文档写入时建立关联 |
| 供应商质量/服务分用代理指标 | 接入真实检验/退货/准时率 |
| pgvector 与业务库同库 | 拆独立向量库 |
| 忠实度依赖 LLM 裁判 | n-gram 校验 / RAGAS |
| `@Scheduled` 单机定时，集群会重复执行 | 分布式锁 / xxl-job |
| 文档 upsert 非原子 | `ON CONFLICT DO UPDATE` |

## 项目结构

```
SRM/
├── sql/                        # 建表 + 初始数据
├── src/main/java/com/srm/
│   ├── common/                 # 公共组件（BaseEntity/Result/异常/JwtUtil）
│   ├── config/                 # 配置（MyBatis-Plus/Redis/拦截器）
│   └── modules/                # 业务模块
│       ├── supplier/           # 供应商管理
│       ├── order/              # 采购订单
│       ├── agent/              # AI Agent（Spring AI + 工具）
│       ├── graph/              # Neo4j 图谱分析
│       ├── rag/                # RAG 混合检索 + 评测
│       ├── rfq/                # 询比价(RFQ)
│       ├── cert/               # 资质续期
│       ├── user/               # 用户认证
│       └── system/             # 字典管理
├── src/main/resources/         # 配置 + 评测数据集
└── frontend/                   # Vue 3 前端
```
