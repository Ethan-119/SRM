package com.srm.modules.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端用户 — 公司内部采购部门人员
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_user")
public class User extends BaseEntity {

    /** 用户名（登录账号） */
    private String username;

    /** 密码（BCrypt 加密存储） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 联系电话 */
    private String phone;

    /** 所属部门 */
    private String department;

    /** 是否管理员: 0-普通员工 1-管理员（有最终确认权） */
    private Integer isAdmin;

    /** 状态: 0-禁用 1-启用 */
    private Integer status;
}
