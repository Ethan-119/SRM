package com.srm.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.srm.modules.user.entity.User;

public interface UserService extends IService<User> {

    /** 根据用户名查询用户 */
    User getByUsername(String username);
}
