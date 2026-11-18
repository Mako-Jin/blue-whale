package com.bluewhale.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.job.po.JobDependencyPo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 任务依赖关系 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface JobDependencyMapper extends BaseMapper<JobDependencyPo> {

    /**
     * 物理删除指定下游任务的全部依赖边（全量替换前清理）。
     *
     * <p>依赖配置为「全量替换」语义，逻辑删除会残留行并触发唯一键
     * {@code uk_job_upstream} 冲突，故此处按下游任务物理清理。</p>
     *
     * @param jobId 下游任务ID
     * @return 删除行数
     */
    @Delete("DELETE FROM job_dependency WHERE job_id = #{jobId}")
    int purgeByJobId(@Param("jobId") Long jobId);
}
