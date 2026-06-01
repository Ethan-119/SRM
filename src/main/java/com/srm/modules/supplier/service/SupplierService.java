package com.srm.modules.supplier.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.srm.modules.supplier.dto.SupplierPageDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.srm.modules.supplier.entity.Supplier;

public interface SupplierService extends IService<Supplier> {

    IPage<Supplier> pageList(SupplierPageDTO queryDTO);
}
