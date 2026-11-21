package com.bluewhale.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.po.stat.JobCountPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 定时任务信息 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface JobInfoMapper extends BaseMapper<JobInfoPo> {

    /**
     * 在册任务数与运行中任务数（时辰殿概览 T5.5）
     *
     * <p>两个数一次查出来：分两条 SQL 会让"总数 30、运行中 31"这种不一致出现在屏幕上
     * ——两条查询之间刚好有人启停任务时就对不上。</p>
     *
     * @param tenantId 租户 ID
     * @return 任务数与运行中数量；无数据时两者均为 0
     */
    @Select("SELECT COUNT(*) AS jobs, "
            + "COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS running "
            + "FROM job_info WHERE tenant_id = #{tenantId} AND deleted = 0")
    JobCountPo countJobs(@Param("tenantId") Long tenantId);

    /**
     * 近一段时间内一次都没执行的运行中任务数（时辰殿概览 T5.5）
     *
     * <p>用 {@code NOT EXISTS} 而不是 {@code LEFT JOIN ... HAVING MAX(start_time) < ?}：
     * 后者在"从未执行过"的任务上 {@code MAX} 为 NULL、比较结果也为 NULL，
     * 这些最该被点名的任务反而会被过滤掉。</p>
     *
     * <p>判据只看"有没有执行记录"，不看 cron 周期。周级任务在 24 小时窗口里本来就该是空的，
     * 所以这个数字**只用于展示**；告警只在"全部运行中任务都静默"时才发
     * （那种情况是调度器停摆，不是某个任务周期长）。</p>
     *
     * @param tenantId 租户 ID
     * @param since    观察窗口起点（含）
     * @return 静默任务数
     */
    @Select("SELECT COUNT(*) FROM job_info j "
            + "WHERE j.tenant_id = #{tenantId} AND j.deleted = 0 AND j.status = 1 "
            + "AND NOT EXISTS (SELECT 1 FROM job_log l "
            + "WHERE l.job_id = j.id AND l.tenant_id = j.tenant_id AND l.deleted = 0 "
            + "AND l.start_time >= #{since})")
    long countSilentRunning(@Param("tenantId") Long tenantId, @Param("since") LocalDateTime since);
}
