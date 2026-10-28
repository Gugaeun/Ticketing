package com.ticketing.notification;

import com.ticketing.performance.Schedule;
import com.ticketing.performance.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/** 예매/취소 이벤트를 받아 알림을 저장하고 발송한다. 실패해도 예매에는 영향이 없다. */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final NotificationRepository notificationRepository;
    private final ScheduleRepository scheduleRepository;
    private final NotificationSender sender;

    @Async("notificationExecutor")
    @EventListener
    @Transactional
    public void onConfirmed(ReservationEvents.Confirmed event) {
        notify(event.memberId(), event.scheduleId(), event.quantity(), NotificationType.RESERVATION_CONFIRMED, "예매가 완료되었습니다.");
    }

    @Async("notificationExecutor")
    @EventListener
    @Transactional
    public void onCanceled(ReservationEvents.Canceled event) {
        notify(event.memberId(), event.scheduleId(), event.quantity(), NotificationType.RESERVATION_CANCELED, "예매가 취소되었습니다.");
    }

    private void notify(Long memberId, Long scheduleId, int quantity, NotificationType type, String suffix) {
        try {
            Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
            String message = "[%s] %s · %s · %d매 %s".formatted(
                    type == NotificationType.RESERVATION_CONFIRMED ? "예매 완료" : "예매 취소",
                    schedule.getPerformance().getTitle(),
                    schedule.getStartAt().format(FORMAT),
                    quantity, suffix);
            notificationRepository.save(new Notification(memberId, type, message));
            sender.send(memberId, message);
        } catch (Exception e) {
            log.warn("알림 처리 실패 memberId={} scheduleId={}", memberId, scheduleId, e);
        }
    }
}
