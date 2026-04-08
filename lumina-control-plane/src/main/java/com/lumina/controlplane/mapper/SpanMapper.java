package com.lumina.controlplane.mapper;

import com.lumina.controlplane.entity.SpanEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Span 链路追踪 Mapper
 */
public interface SpanMapper extends BaseMapper<SpanEntity> {

    @Select("SELECT trace_id, MAX(start_time) as max_time FROM lumina_span GROUP BY trace_id ORDER BY max_time DESC LIMIT #{limit}")
    List<Object[]> findRecentTraceIdsWithTime(@Param("limit") int limit);

    @Select("SELECT * FROM lumina_span WHERE trace_id = #{traceId} ORDER BY start_time ASC")
    List<SpanEntity> findByTraceIdOrderByStartTimeAsc(@Param("traceId") String traceId);

    @Select("SELECT * FROM lumina_span WHERE service_name = #{serviceName} " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime} ORDER BY start_time DESC")
    List<SpanEntity> findByServiceNameAndTimeRange(
            @Param("serviceName") String serviceName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT DISTINCT trace_id, MIN(start_time) as first_time FROM lumina_span " +
            "WHERE created_at >= #{startTime} AND created_at <= #{endTime} " +
            "GROUP BY trace_id ORDER BY first_time DESC")
    List<Object[]> findDistinctTraceIdsByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    void deleteByCreatedAtBefore(@Param("time") LocalDateTime time);
}