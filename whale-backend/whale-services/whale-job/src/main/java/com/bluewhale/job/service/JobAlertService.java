package com.bluewhale.job.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.dto.JobAlertQueryRequest;
import com.bluewhale.job.vo.JobAlertVO;

/**
 * 任务告警记录服务（只读）。
 *
 * @author BlueWhale
 */
public interface JobAlertService {

    /**
     * 分页查询任务告警记录。
     *
     * @param request 查询条件
     * @return 告警记录分页结果
     */
    PageResult<JobAlertVO> page(JobAlertQueryRequest request);
}
