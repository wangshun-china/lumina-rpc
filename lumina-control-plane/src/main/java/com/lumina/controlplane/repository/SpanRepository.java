package com.lumina.controlplane.repository;

import com.lumina.controlplane.entity.SpanEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Span Repository
 */
@Repository
public interface SpanRepository extends JpaRepository<SpanEntity, Long> {

    /**
     * 查询最近的 Trace 列表（使用子查询解决 DISTINCT + ORDER BY 问题）
     */
    @Query("SELECT s.traceId, MAX(s.startTime) as maxTime FROM SpanEntity s GROUP BY s.traceId ORDER BY maxTime DESC")
    List<Object[]> findRecentTraceIdsWithTime(Pageable pageable);

    /**
     * 根据 Trace ID 查询所有 Span（按开始时间排序）
     */
    List<SpanEntity> findByTraceIdOrderByStartTimeAsc(String traceId);

    /**
     * 查询指定服务在时间范围内的 Span
     */
    @Query("SELECT s FROM SpanEntity s WHERE s.serviceName = :serviceName " +
           "AND s.createdAt >= :startTime AND s.createdAt <= :endTime ORDER BY s.startTime DESC")
    List<SpanEntity> findByServiceNameAndTimeRange(
            @Param("serviceName") String serviceName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询时间范围内的所有 Trace ID（去重，按时间倒序）
     */
    @Query("SELECT DISTINCT s.traceId, MIN(s.startTime) as firstTime FROM SpanEntity s " +
           "WHERE s.createdAt >= :startTime AND s.createdAt <= :endTime " +
           "GROUP BY s.traceId ORDER BY firstTime DESC")
    List<Object[]> findDistinctTraceIdsByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 删除指定时间之前的数据
     */
    void deleteByCreatedAtBefore(LocalDateTime time);
}