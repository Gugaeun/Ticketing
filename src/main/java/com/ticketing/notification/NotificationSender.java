package com.ticketing.notification;

/** 외부 발송(이메일, 푸시 등) 추상화. 지금은 로그로 대체하고, 나중에 실제 구현이나 메시지 큐로 교체한다. */
public interface NotificationSender {

    void send(Long memberId, String message);
}
