package com.lumina.controlplane.mapper;

import com.lumina.controlplane.entity.RequestStatsEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 请求统计 Mapper
 */
public interface RequestStatsMapper extends BaseMapper<RequestStatsEntity> {

    @Select("SELECT * FROM lumina_request_stats WHERE service_name = #{serviceName} " +
            "AND stat_time >= #{startTime} AND stat_time <= #{endTime} ORDER BY stat_time ASC")
    List<RequestStatsEntity> findByServiceNameAndTimeRange(
            @Param("serviceName") String serviceName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT service_name, SUM(total_requests), SUM(success_count), SUM(fail_count), " +
            "AVG(total_latency * 1.0 / NULLIF(total_requests, 0)) " +
            "FROM lumina_request_stats WHERE stat_time >= #{startTime} AND stat_time <= #{endTime} " +
            "GROUP BY service_name")
    List<Object[]> aggregateByService(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT stat_time, SUM(total_requests), SUM(success_count), SUM(fail_count), SUM(total_latency) " +
            "FROM lumina_request_stats WHERE stat_time >= #{startTime} AND stat_time <= #{endTime} " +
            "GROUP BY stat_time ORDER BY stat_time ASC")
    List<Object[]> aggregateByTime(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Delete("DELETE FROM lumina_request_stats WHERE stat_time < #{time}")
    void deleteByStatTimeBefore(@Param("time") LocalDateTime time);
}