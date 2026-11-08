package com.bluewhale.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.job.entity.JobExecutor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 执行器注册表 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface JobExecutorMapper extends BaseMapper<JobExecutor> {

    /**
     * 物理删除心跳过期的执行器行。
     *
     * <p>必须物理删除：唯一键 {@code (tenant_id, app_name, address)} 不含 {@code deleted}，
     * 逻辑删除会把同一地址的重新注册挡在唯一键上（容器重建后地址可能复用）。</p>
     *
     * @param threshold 心跳有效下限，早于该时间的行被删除
     * @return 删除行数
     */
    @Delete("DELETE FROM job_executor WHERE last_beat < #{threshold}")
    int deleteStale(@Param("threshold") LocalDateTime threshold);
}
