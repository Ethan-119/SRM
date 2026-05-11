package com.srm.modules.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.srm.modules.user.entity.User;
import com.srm.modules.user.mapper.UserMapper;
import com.srm.modules.user.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
