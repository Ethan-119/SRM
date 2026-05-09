package com.srm.modules.supplier.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.mapper.SupplierMapper;
import com.srm.modules.supplier.service.SupplierService;
import org.springframework.stereotype.Service;

@Service
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {
}
