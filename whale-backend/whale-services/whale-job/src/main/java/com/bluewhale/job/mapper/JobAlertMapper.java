package com.bluewhale.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.job.entity.JobAlert;

/**
 * 任务失败告警记录 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * @author BlueWhale
 */
public interface JobAlertMapper extends BaseMapper<JobAlert> {
}
