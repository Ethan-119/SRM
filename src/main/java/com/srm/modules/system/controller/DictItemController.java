package com.srm.modules.system.controller;

import com.srm.common.Result;
import com.srm.modules.system.entity.DictItem;
import com.srm.modules.system.service.DictItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "字典管理")
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictItemController {

    private final DictItemService dictItemService;

    @Operation(summary = "查询字典列表")
    @GetMapping
    public Result<List<DictItem>> list() {
        return Result.ok(dictItemService.list());
    }

    @Operation(summary = "根据ID查询字典项")
    @GetMapping("/{id}")
    public Result<DictItem> getById(@PathVariable Long id) {
        return Result.ok(dictItemService.getById(id));
    }

    @Operation(summary = "新增字典项")
    @PostMapping
    public Result<Void> save(@RequestBody DictItem dictItem) {
        dictItemService.save(dictItem);
        return Result.ok();
    }

    @Operation(summary = "更新字典项")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DictItem dictItem) {
        dictItem.setId(id);
        dictItemService.updateById(dictItem);
        return Result.ok();
    }

    @Operation(summary = "删除字典项")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictItemService.removeById(id);
        return Result.ok();
    }
}
