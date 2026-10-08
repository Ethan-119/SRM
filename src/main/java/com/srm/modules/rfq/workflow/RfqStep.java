package com.srm.modules.rfq.workflow;

/**
 * 询比价工作流步骤链中的一步。
 * 新增步骤只需实现本接口 + 标注 {@code @Component} + {@code @Order(N)}，N 决定执行顺序。
 */
public interface RfqStep {

    /** 步骤名称，用于日志与错误记录 */
    String name();

    /** 执行步骤：从 ctx 读输入、把结果写回 ctx */
    StepResult execute(RfqContext ctx);

    /** 失败时是否继续后续步骤（降级），默认 false = 中断 */
    default boolean shouldContinueOnFail() {
        return false;
    }

    /** 是否为「记录/兜底」步骤：前面任一硬失败后仍会执行该步骤，其余步骤跳过 */
    default boolean isRecorder() {
        return false;
    }
}
