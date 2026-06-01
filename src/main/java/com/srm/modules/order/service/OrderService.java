package com.srm.modules.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.srm.modules.order.dto.OrderPageDTO;
import com.srm.modules.order.entity.Order;

public interface OrderService extends IService<Order> {

    IPage<Order> pageList(OrderPageDTO queryDTO);
}
