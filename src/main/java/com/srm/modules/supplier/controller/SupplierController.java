package com.srm.modules.supplier.controller;

import com.srm.common.Result;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "供应商管理")
@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @Operation(summary = "查询供应商列表")
    @GetMapping
    public Result<List<Supplier>> list() {
        return Result.ok(supplierService.list());
    }

    @Operation(summary = "根据ID查询供应商")
    @GetMapping("/{id}")
    public Result<Supplier> getById(@PathVariable Long id) {
        return Result.ok(supplierService.getById(id));
    }

    @Operation(summary = "新增供应商")
    @PostMapping
    public Result<Void> save(@RequestBody Supplier supplier) {
        supplierService.save(supplier);
        return Result.ok();
    }

    @Operation(summary = "更新供应商")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Supplier supplier) {
        supplier.setId(id);
        supplierService.updateById(supplier);
        return Result.ok();
    }

    @Operation(summary = "删除供应商")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.removeById(id);
        return Result.ok();
    }
}
