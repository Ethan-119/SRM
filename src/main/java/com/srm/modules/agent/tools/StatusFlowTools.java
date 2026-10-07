package com.srm.modules.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 状态机流转规则工具。
 */
@Component
public class StatusFlowTools {

    @Tool(description = "获取供应商全生命周期状态流转规则")
    public String getSupplierStatusFlow() {
        return "供应商状态流转规则：\n"
                + "0-注册 → 1-待审核（新供应商注册后进入待审核状态）\n"
                + "1-待审核 → 2-已准入（审核通过，进入准入状态）\n"
                + "1-待审核 → 5-黑名单（审核不通过，可拉黑）\n"
                + "2-已准入 → 3-合作中（开始正式合作）\n"
                + "3-合作中 → 4-冻结（合作出现问题，暂时冻结）\n"
                + "4-冻结 → 3-合作中（问题解决，恢复合作）\n"
                + "4-冻结 → 5-黑名单（严重违规，拉入黑名单）\n"
                + "注意：禁止越级变更（除待审核不通过外）。";
    }

    @Tool(description = "获取采购订单状态流转规则")
    public String getOrderStatusFlow() {
        return "采购订单状态流转规则：\n"
                + "待确认 → 生产中（供应商确认接单后进入生产）\n"
                + "生产中 → 已发货（生产完成并发货）\n"
                + "已发货 → 已签收（采购方确认收货）\n"
                + "注意：\n"
                + "1. 严格禁止越级变更状态（如从'待确认'直接变为'已发货'）。\n"
                + "2. 每个状态变更都需要对应的业务操作和凭证。\n"
                + "3. '已签收'为终态，不可再变更。";
    }
}
