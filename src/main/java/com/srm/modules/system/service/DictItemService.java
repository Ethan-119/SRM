package com.srm.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.srm.modules.system.entity.DictItem;

import java.util.List;

public interface DictItemService extends IService<DictItem> {

    /** 按字典类型查询 — 走 Redis 缓存 */
    List<DictItem> listByType(String dictType);
}
