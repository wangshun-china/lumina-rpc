package com.lumina.controlplane.repository;

import com.lumina.controlplane.entity.RequestStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 请求统计 Repository
 */
@Repository
public interface RequestStatsRepository extends JpaRepository<RequestStatsEntity, Long> {

    /**
     * 查询指定服务在时间范围内的统计数据
     */
    @Query("SELECT s FROM RequestStatsEntity s WHERE s.serviceName = :serviceName " +
           "AND s.statTime >= :startTime AND s.statTime <= :endTime ORDER BY s.statTime ASC")
    List<RequestStatsEntity> findByServiceNameAndTimeRange(
            @Param("serviceName") String serviceName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询所有服务在时间范围内的聚合统计数据
     */
    @Query("SELECT s.serviceName, SUM(s.totalRequests), SUM(s.successCount), SUM(s.failCount), " +
           "AVG(s.totalLatency * 1.0 / NULLIF(s.totalRequests, 0)) " +
           "FROM RequestStatsEntity s WHERE s.statTime >= :startTime AND s.statTime <= :endTime " +
           "GROUP BY s.serviceName")
    List<Object[]> aggregateByService(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询时间范围内的全局统计数据（按时间分组）
     */
    @Query("SELECT s.statTime, SUM(s.totalRequests), SUM(s.successCount), SUM(s.failCount), " +
           "SUM(s.totalLatency) " +
           "FROM RequestStatsEntity s WHERE s.statTime >= :startTime AND s.statTime <= :endTime " +
           "GROUP BY s.statTime ORDER BY s.statTime ASC")
    List<Object[]> aggregateByTime(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 删除指定时间之前的统计数据
     */
    void deleteByStatTimeBefore(LocalDateTime time);
}