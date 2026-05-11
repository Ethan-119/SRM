package com.srm.modules.order.controller;

import com.srm.common.Result;
import com.srm.modules.order.dto.OrderDTO;
import com.srm.modules.order.entity.Order;
import com.srm.modules.order.service.OrderService;
import com.srm.modules.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "采购订单管理")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "查询订单列表")
    @GetMapping
    public Result<List<OrderVO>> list() {
        List<Order> orders = orderService.list();
        List<OrderVO> vos = orders.stream().map(this::toVO).toList();
        return Result.ok(vos);
    }

    @Operation(summary = "根据ID查询订单")
    @GetMapping("/{id}")
    public Result<OrderVO> getById(@PathVariable Long id) {
        Order order = orderService.getById(id);
        return Result.ok(toVO(order));
    }

    @Operation(summary = "新增订单")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody OrderDTO dto) {
        Order order = new Order();
        BeanUtils.copyProperties(dto, order);
        orderService.save(order);
        return Result.ok();
    }

    @Operation(summary = "更新订单")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody OrderDTO dto) {
        Order order = new Order();
        BeanUtils.copyProperties(dto, order);
        order.setId(id);
        orderService.updateById(order);
        return Result.ok();
    }

    @Operation(summary = "删除订单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        orderService.removeById(id);
        return Result.ok();
    }

    private OrderVO toVO(Order order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }
}
