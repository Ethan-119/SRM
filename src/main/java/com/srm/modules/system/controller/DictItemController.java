package com.srm.modules.system.controller;

import com.srm.common.Result;
import com.srm.modules.system.dto.DictItemDTO;
import com.srm.modules.system.entity.DictItem;
import com.srm.modules.system.service.DictItemService;
import com.srm.modules.system.vo.DictItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "字典管理")
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictItemController {

    private final DictItemService dictItemService;

    @Operation(summary = "按类型查询字典项（走 Redis 缓存）")
    @GetMapping("/type/{dictType}")
    public Result<List<DictItemVO>> listByType(@PathVariable String dictType) {
        List<DictItem> items = dictItemService.listByType(dictType);
        List<DictItemVO> vos = items.stream().map(this::toVO).toList();
        return Result.ok(vos);
    }

    @Operation(summary = "查询字典列表")
    @GetMapping
    public Result<List<DictItemVO>> list() {
        List<DictItem> items = dictItemService.list();
        List<DictItemVO> vos = items.stream().map(this::toVO).toList();
        return Result.ok(vos);
    }

    @Operation(summary = "根据ID查询字典项")
    @GetMapping("/{id}")
    public Result<DictItemVO> getById(@PathVariable Long id) {
        DictItem item = dictItemService.getById(id);
        return Result.ok(toVO(item));
    }

    @Operation(summary = "新增字典项（自动删缓存）")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody DictItemDTO dto) {
        DictItem item = new DictItem();
        BeanUtils.copyProperties(dto, item);
        dictItemService.save(item);
        return Result.ok();
    }

    @Operation(summary = "更新字典项（自动删缓存）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DictItemDTO dto) {
        DictItem item = new DictItem();
        BeanUtils.copyProperties(dto, item);
        item.setId(id);
        dictItemService.updateById(item);
        return Result.ok();
    }

    @Operation(summary = "删除字典项（自动删缓存）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictItemService.removeById(id);
        return Result.ok();
    }

    private DictItemVO toVO(DictItem item) {
        DictItemVO vo = new DictItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }
}
