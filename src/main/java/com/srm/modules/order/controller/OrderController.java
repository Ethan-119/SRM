package com.srm.modules.order.controller;

import com.srm.common.Result;
import com.srm.modules.order.entity.Order;
import com.srm.modules.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
    public Result<List<Order>> list() {
        return Result.ok(orderService.list());
    }

    @Operation(summary = "根据ID查询订单")
    @GetMapping("/{id}")
    public Result<Order> getById(@PathVariable Long id) {
        return Result.ok(orderService.getById(id));
    }

    @Operation(summary = "新增订单")
    @PostMapping
    public Result<Void> save(@RequestBody Order order) {
        orderService.save(order);
        return Result.ok();
    }

    @Operation(summary = "更新订单")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Order order) {
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
}
