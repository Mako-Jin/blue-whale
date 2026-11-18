package com.bluewhale.job.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.dto.JobLogQueryRequest;
import com.bluewhale.job.vo.ExecutorLogVO;
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

    /**
     * 回捞某次执行在执行器侧保存的完整日志。
     *
     * <p>执行流水的 {@code result} / {@code error_msg} 落库时截断到 2000 字，堆栈与业务日志
     * 只在执行器本地。本方法按 {@code job_log.executor_address} 精确定位实例后取回正文。</p>
     *
     * @param logId 执行流水ID
     * @return 回捞结果（含是否取到与原因）
     */
    ExecutorLogVO executorLog(Long logId);
}
