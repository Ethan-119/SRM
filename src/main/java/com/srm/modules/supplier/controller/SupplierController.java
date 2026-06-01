package com.srm.modules.supplier.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.srm.common.PageResult;
import com.srm.common.Result;
import com.srm.modules.supplier.dto.SupplierDTO;
import com.srm.modules.supplier.dto.SupplierPageDTO;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import com.srm.modules.supplier.vo.SupplierVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "供应商管理")
@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @Operation(summary = "查询供应商列表")
    @GetMapping
    public Result<List<SupplierVO>> list() {
        List<Supplier> suppliers = supplierService.list();
        List<SupplierVO> vos = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            vos.add(toVO(supplier));
        }
        return Result.ok(vos);
    }

    @Operation(summary = "分页查询供应商列表（XML）")
    @GetMapping("/page")
    public Result<PageResult<SupplierVO>> page(SupplierPageDTO queryDTO) {
        IPage<Supplier> pageData = supplierService.pageList(queryDTO);
        List<SupplierVO> records = new ArrayList<>();
        for (Supplier supplier : pageData.getRecords()) {
            records.add(toVO(supplier));
        }
        long current = pageData.getCurrent();
        long size = pageData.getSize();
        long total = pageData.getTotal();
        PageResult<SupplierVO> result = PageResult.of(current, size, total, records);
        return Result.ok(result);
    }

    @Operation(summary = "根据ID查询供应商")
    @GetMapping("/{id}")
    public Result<SupplierVO> getById(@PathVariable Long id) {
        Supplier supplier = supplierService.getById(id);
        return Result.ok(toVO(supplier));
    }

    @Operation(summary = "新增供应商")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody SupplierDTO dto) {
        Supplier supplier = new Supplier();
        BeanUtils.copyProperties(dto, supplier);
        supplierService.save(supplier);
        return Result.ok();
    }

    @Operation(summary = "更新供应商")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SupplierDTO dto) {
        Supplier supplier = new Supplier();
        BeanUtils.copyProperties(dto, supplier);
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

    private SupplierVO toVO(Supplier supplier) {
        SupplierVO vo = new SupplierVO();
        BeanUtils.copyProperties(supplier, vo);
        return vo;
    }
}
