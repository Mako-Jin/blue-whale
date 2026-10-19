package com.bluewhale.job.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.dto.JobLogQueryRequest;
import com.bluewhale.job.vo.JobLogVO;

/**
 * 定时任务执行日志服务（只读）
 *
 * @author BlueWhale
 */
public interface JobLogService {

    /**
     * 分页查询执行日志
     *
     * @param request 查询条件
     * @return 执行日志分页结果
     */
    PageResult<JobLogVO> page(JobLogQueryRequest request);
}
