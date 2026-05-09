package com.srm.modules.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.srm.modules.system.entity.DictItem;
import com.srm.modules.system.mapper.DictItemMapper;
import com.srm.modules.system.service.DictItemService;
import org.springframework.stereotype.Service;

@Service
public class DictItemServiceImpl extends ServiceImpl<DictItemMapper, DictItem> implements DictItemService {
}
