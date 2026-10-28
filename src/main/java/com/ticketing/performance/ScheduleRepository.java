package com.ticketing.performance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByPerformanceIdOrderByStartAt(Long performanceId);

    void deleteByPerformanceId(Long performanceId);

    /** 공연 목록 한 페이지의 회차를 한 번에 가져온다 (공연마다 따로 조회하는 N+1 방지). */
    @Query("select s from Schedule s join fetch s.performance where s.performance.id in :ids order by s.startAt")
    List<Schedule> findByPerformanceIds(@Param("ids") java.util.Collection<Long> ids);

    /** 낙관적 락(CAS): 읽은 version 이 그대로일 때만 차감한다. 0 이 반환되면 그 사이 다른 요청이 먼저 갱신한 것. */
    @Modifying
    @Query("update Schedule s set s.remainingStock = s.remainingStock - :quantity, s.version = s.version + 1 "
            + "where s.id = :id and s.version = :version and s.remainingStock >= :quantity")
    int decreaseStockIfVersionMatches(@Param("id") Long id, @Param("quantity") int quantity,
                                      @Param("version") long version);

    @Modifying
    @Query("update Schedule s set s.remainingStock = s.remainingStock + :quantity, s.version = s.version + 1 "
            + "where s.id = :id")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.id = :id")
    Optional<Schedule> findByIdForUpdate(@Param("id") Long id);
}
