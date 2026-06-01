package com.srm.modules.user.vo;

import lombok.Data;

@Data
public class LoginVO {

    /** JWT Token */
    private String token;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 是否管理员 */
    private Integer isAdmin;
}
