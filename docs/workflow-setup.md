# 智能询比价 + 资质续期工作流 — 实施清单

> 本文档列出「你需要手动完成的事项」以及本次实现的关键决策，帮助你把这套工作流真正跑起来。

---

## 一、本次已实现（代码已写好，无需再写）

### 场景一：智能询比价全自动闭环（`com.srm.modules.rfq`）

| 文件 | 说明 |
|---|---|
| `entity/RfqWorkflow` | 询比价实例表实体（JSON 存供应商/报价/分析结果） |
| `enums/RfqStatus` | QUOTING/ANALYZING/PENDING_CONFIRM/ORDERED/REJECTED/EXPIRED |
| `service/RfqWorkflowService` | 全自动状态机：创建→报价→AI分析→确认→自动下单 |
| `service/SupplierScoringService` | 综合评分（价格 50% + 交期 20% + 质量 30%） |
| `controller/RfqWorkflowController` | REST 接口 |
| `sql/workflow.sql` | `srm_rfq_workflow` 建表脚本 |

### 场景二：供应商资质到期自动续期（`com.srm.modules.cert`）

| 文件 | 说明 |
|---|---|
| `service/CertRenewalWorkflowService` | 定时扫描→通知→AI审核→更新图谱→超时冻结 |
| `controller/CertRenewalController` | 供应商上传新资质接口 |
| `graph/repository/Neo4jSupplierRepository` | 关联风险/集中度/资质到期查询 |
| `graph/vo/CertExpiryAlert` | 到期提醒对象 |

### 支撑改动

- `NotificationService`：通知服务（**日志桩**，未接短信/邮件）
- `SrmApplication`：新增 `@EnableAsync` / `@EnableScheduling`
- `config/AsyncConfig`：异步线程池
- `GraphRepository`：新增 `write()` 方法（写 Cypher）

---

## 二、需要你手动完成的事项

- [ ] **1. 执行建表 SQL**
  ```sql
  -- 连接 MySQL srm 库，执行
  source sql/workflow.sql;
  ```

- [ ] **2. 初始化 Neo4j（约束 + 种子数据）**
  在 Neo4j Browser / cypher-shell 依次执行：
  - `src/main/resources/graph/schema.cypher`（约束与索引）
  - `src/main/resources/graph/seed.cypher`（演示节点与关系）

- [ ] **3. 同步 Neo4j 与 MySQL 的供应商 ID（关键）**
  种子数据里 `Supplier.id = 1001/1002/1003/1004`，但 MySQL 供应商主键是雪花 ID。
  关联风险、集中度校验都按 `Supplier.id` 关联，**两者不一致时图谱校验查不到数据**。
  需要你写一个同步脚本（或直接改 seed 里的 id 对齐 MySQL `srm_supplier.id`）。

- [ ] **4. 配置环境变量**
  ```bash
  DASHSCOPE_API_KEY=xxx          # 必填，否则 AI 报告/资质审核走降级文案
  NEO4J_URI=bolt://localhost:7687
  NEO4J_USERNAME=neo4j
  NEO4J_PASSWORD=neo4j123456
  NEO4J_DATABASE=SRM
  ```

- [ ] **5. 造一条「30 天内到期」的资质数据（场景二才会出结果）**
  种子数据里 `cert1` 有效期到 2026-12-31（约 95 天后）、`cert2` 已过期，
  **当前日期下 30 天扫描窗口内没有任何资质命中**。要验证续期流程，需手动把某条资质的
  `expireDate` 改成未来 30 天内，例如：
  ```cypher
  MATCH (c:Cert {certNo:'ISO9001-A'}) SET c.expireDate = '2026-10-15';
  ```

- [ ] **6. 重启应用**，使 `@EnableScheduling` / `@EnableAsync` 生效。

---

## 三、接口测试流程

### 场景一：询比价闭环

```bash
# 1. 创建询比价单（3 家供应商）
curl -X POST http://localhost:8083/api/rfq \
  -H 'Content-Type: application/json' \
  -d '{"materialName":"冷轧钢板","quantity":500,"supplierIds":[1001,1002,1003],"quoteHours":24}'

# 2. 供应商报价（3 家分别报）
curl -X POST http://localhost:8083/api/rfq/quote \
  -H 'Content-Type: application/json' \
  -d '{"rfqId":<上一步返回的id>,"supplierId":1001,"price":95,"taxRate":0.13,"deliveryDays":15}'
# ... 1002、1003 同理

# 3. 报价收齐后，调度线程会在 30s 内自动分析；也可手动触发
curl -X POST http://localhost:8083/api/rfq/<id>/analyze

# 4. 查看分析结果
curl http://localhost:8083/api/rfq/<id>

# 5. 一键确认（自动生成订单）
curl -X POST http://localhost:8083/api/rfq/<id>/confirm \
  -H 'Content-Type: application/json' \
  -d '{"managerId":1,"approved":true,"comment":""}'
```

### 场景二：资质续期

```bash
curl -X POST "http://localhost:8083/api/cert/review?supplierId=1001&certType=ISO9001&certNo=ISO9001-A&newExpireDate=2027-12-31"
```

---

## 四、关键决策（与原始方案不同处）

| 原方案 | 实际实现 | 原因 |
|---|---|---|
| `material_id BIGINT` | `material_name VARCHAR` | 项目无物料主数据表；`Order` 用 `material_name`；Neo4j 风险分析按 `Material.name` 查 |
| fastjson `JSON.toJSONString` | Hutool `JSONUtil` | 项目只引入了 Hutool，无 fastjson 依赖 |
| `NotificationService` 短信/邮件 | 日志桩 | 项目无短信/邮件基础设施，先打日志占位 |
| 资质图片多模态审核 | 文本化审核（基础规则 + LLM） | `qwen3-max` 为文本模型，不支持图片输入 |
| `RfqEvent` 枚举 | 未创建 | 状态机直接用方法驱动，事件枚举是死代码 |
| `@Async` + `CompletableFuture` 混用 | 调度线程 + `@Async` 分离 | 避免自调用代理失效与事务竞态 |

---

## 五、遗留 / 后续可做（非本次范围）

1. **供应商 ID 映射**：雪花 ID ↔ Neo4j `1001-1004` 的同步机制（需写同步脚本）。
2. **通知接真实渠道**：`NotificationService` 接入短信 / 邮件 / 站内信。
3. **资质图片多模态审核**：换支持视觉的模型后，恢复 `Media` 图片审核。
4. **单号并发安全**：`rfqNo` / `orderNo` 用 `System.currentTimeMillis() % 100000` 生成，
   高并发下可能撞 `uk_rfq_no` 唯一索引，建议换成 Redis 自增或数据库序列。
5. **询比价数量精度**：`RfqWorkflow.quantity` 为 `DECIMAL(18,4)`，但 `Order.quantity` 是 `INT`，
   下单时做了 `intValue()` 截断，非整数数量会丢精度。
