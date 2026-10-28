package com.ticketing.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(Long memberId, String message) {
        log.info("[알림 발송] memberId={} message={}", memberId, message);
    }
}
