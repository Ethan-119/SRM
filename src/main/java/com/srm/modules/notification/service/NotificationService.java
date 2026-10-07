package com.srm.modules.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通知服务（日志桩）。后续可接入短信 / 邮件 / 站内信。
 */
@Service
@Slf4j
public class NotificationService {

    public void notifySupplier(Long supplierId, String message) {
        log.info("[通知供应商:{}] {}", supplierId, message);
    }

    public void notifyManager(Long managerId, String message) {
        log.info("[通知采购经理:{}] {}", managerId, message);
    }

    public void notifyManager(String message) {
        log.info("[通知采购经理] {}", message);
    }
}
