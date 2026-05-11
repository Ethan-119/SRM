package com.srm.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户 DTO — 接收前端请求参数（不含密码，密码单独处理）
 */
@Data
public class UserDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码 — 仅新增时必填，更新时留空表示不修改 */
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    private String email;

    private String phone;

    private String department;

    /** 是否管理员: 0-普通员工 1-管理员 */
    private Integer isAdmin;

    /** 状态: 0-禁用 1-启用 */
    private Integer status;
}
