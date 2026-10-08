package com.srm.modules.rfq.workflow;

import lombok.Getter;

/**
 * 单步执行结果。
 */
@Getter
public class StepResult {

    private final boolean success;

    /** 描述信息，失败时作为错误原因收集 */
    private final String message;

    private StepResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static StepResult success() {
        return new StepResult(true, "成功");
    }

    public static StepResult success(String message) {
        return new StepResult(true, message);
    }

    public static StepResult fail(String message) {
        return new StepResult(false, message);
    }
}
