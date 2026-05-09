package com.srm.modules.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.srm.modules.order.entity.Order;
import com.srm.modules.order.mapper.OrderMapper;
import com.srm.modules.order.service.OrderService;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {
}
