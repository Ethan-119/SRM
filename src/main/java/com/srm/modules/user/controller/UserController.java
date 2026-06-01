package com.srm.modules.user.controller;

import com.srm.common.Result;
import com.srm.common.exception.BusinessException;
import com.srm.modules.user.dto.UserDTO;
import com.srm.modules.user.entity.User;
import com.srm.modules.user.service.UserService;
import com.srm.modules.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "查询用户列表")
    @GetMapping
    public Result<List<UserVO>> list() {
        List<User> users = userService.list();
        List<UserVO> vos = new ArrayList<>();
        for (User user : users) {
            vos.add(toVO(user));
        }
        return Result.ok(vos);
    }

    @Operation(summary = "根据ID查询用户")
    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return Result.ok(toVO(user));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody UserDTO dto) {
        // 新增时密码必填
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(400, "密码不能为空");
        }
        User user = new User();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(BCrypt.withDefaults().hashToString(12, dto.getPassword().toCharArray()));
        user.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        user.setIsAdmin(dto.getIsAdmin() != null ? dto.getIsAdmin() : 0);
        userService.save(user);
        return Result.ok();
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        BeanUtils.copyProperties(dto, user, "password", "username");
        // 填写了密码才更新
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(BCrypt.withDefaults().hashToString(12, dto.getPassword().toCharArray()));
        }
        user.setId(id);
        userService.updateById(user);
        return Result.ok();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        return Result.ok();
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
