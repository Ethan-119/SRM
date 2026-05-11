package com.srm.modules.user.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户 VO — 返回给前端的展示数据（隐藏密码、isDeleted）
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String username;

    private String realName;

    private String email;

    private String phone;

    private String department;

    private Integer isAdmin;

    private Integer status;
}
