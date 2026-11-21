package com.bluewhale.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.job.po.JobLogPo;
import com.bluewhale.job.po.stat.JobDayRunPo;
import com.bluewhale.job.po.stat.JobFailTopPo;
import com.bluewhale.job.po.stat.JobRunStatPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务执行日志 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface JobLogMapper extends BaseMapper<JobLogPo> {

    /**
     * 时间窗内的执行统计（时辰殿概览 T5.5）
     *
     * <p>只数**已结束**的执行（{@code status IN (1, -1)}）：执行中的行结束时才回写，
     * 把它算进去会让"今日执行 20 次、成功率 60%"在几秒内反复跳，看的人分不清是故障还是刷新。</p>
     *
     * <p>条件列不套函数（{@code >= / <}），保住 {@code idx_tenant_start_time}；
     * 按天聚合那条才用 {@code DATE(start_time)}，因为窗口已收窄到 7 天。</p>
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 总数与成功/失败次数；无数据时全为 0
     */
    @Select("SELECT COUNT(*) AS totalRuns, "
            + "COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS successRuns, "
            + "COALESCE(SUM(CASE WHEN status = -1 THEN 1 ELSE 0 END), 0) AS failureRuns "
            + "FROM job_log "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND status IN (1, -1) "
            + "AND start_time >= #{from} AND start_time < #{to}")
    JobRunStatPo sumRunsBetween(@Param("tenantId") Long tenantId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    /**
     * 时间窗内出现过失败的任务数（时辰殿概览 T5.5）
     *
     * <p>与失败次数分开算：失败 20 次可能是同一个任务在死循环重试，
     * 也可能是 20 个任务一起挂——前者改一个配置就好，后者要去看共享依赖。</p>
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 去重后的任务数
     */
    @Select("SELECT COUNT(DISTINCT job_id) FROM job_log "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = -1 "
            + "AND start_time >= #{from} AND start_time < #{to}")
    long countFailedJobs(@Param("tenantId") Long tenantId,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

    /**
     * 按天聚合的执行次数（时辰殿概览 T5.5）
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @return 有数据的日期各一行（缺的日期不补，由调用方补 0）
     */
    @Select("SELECT DATE(start_time) AS day, COUNT(*) AS runs FROM job_log "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status IN (1, -1) "
            + "AND start_time >= #{from} AND start_time < #{to} "
            + "GROUP BY DATE(start_time) ORDER BY day")
    List<JobDayRunPo> countRunsByDay(@Param("tenantId") Long tenantId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    /**
     * 时间窗内失败次数最多的任务（时辰殿概览 T5.5）
     *
     * <p>按 {@code job_name} 分组而不是 {@code job_id}：同一任务改名后会留下两个
     * job_id 的历史行，按 id 分组会让它占掉两个坑位，看起来像"两个任务都在失败"。</p>
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻（含）
     * @param to       截止时刻（不含）
     * @param limit    取前几名
     * @return 按失败次数降序
     */
    @Select("SELECT job_name AS jobName, COUNT(*) AS failures FROM job_log "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = -1 "
            + "AND start_time >= #{from} AND start_time < #{to} "
            + "GROUP BY job_name ORDER BY failures DESC LIMIT #{limit}")
    List<JobFailTopPo> topFailedJobs(@Param("tenantId") Long tenantId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to,
                                     @Param("limit") int limit);
}
