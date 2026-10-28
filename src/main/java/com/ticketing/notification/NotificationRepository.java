package com.ticketing.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop30ByMemberIdOrderByIdDesc(Long memberId);

    long countByMemberIdAndReadFalse(Long memberId);

    @Transactional
    @Modifying
    @Query("update Notification n set n.read = true where n.memberId = :memberId and n.read = false")
    int markAllRead(@Param("memberId") Long memberId);
}
