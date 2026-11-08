package com.bluewhale.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.job.entity.JobExecutor;
import org.apache.ibatis.annotations.Mapper;

/**
 * 执行器注册表 Mapper
 *
 * @author BlueWhale
 */
@Mapper
public interface JobExecutorMapper extends BaseMapper<JobExecutor> {
}
