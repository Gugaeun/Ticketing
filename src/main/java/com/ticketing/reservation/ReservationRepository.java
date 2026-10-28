package com.ticketing.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByMemberId(Long memberId);

    long countByScheduleIdAndStatus(Long scheduleId, ReservationStatus status);

    long countByStatus(ReservationStatus status);

    /** 회차별 확정 판매 수량: [scheduleId, sum(quantity)] */
    @Query("select r.scheduleId, sum(r.quantity) from Reservation r where r.status = :status group by r.scheduleId")
    List<Object[]> sumQuantityBySchedule(@Param("status") ReservationStatus status);

    @Transactional
    @Modifying
    @Query("delete from Reservation r where r.scheduleId = :scheduleId")
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);
}
