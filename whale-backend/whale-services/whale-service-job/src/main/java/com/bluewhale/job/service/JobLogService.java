package com.bluewhale.job.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.dto.JobLogQueryRequest;
import com.bluewhale.job.dto.JobLogStatsQueryRequest;
import com.bluewhale.job.vo.ExecutorLogVO;
import com.bluewhale.job.vo.JobLogStatsVO;
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

    /**
     * 执行统计：按天成功/失败数、平均耗时、P95，以及失败与耗时 TopN。
     *
     * <p>只统计已结束的执行（成功/失败），执行中不计入；默认窗口最近 7 天，最大 90 天。</p>
     *
     * @param request 统计条件
     * @return 统计结果
     */
    JobLogStatsVO stats(JobLogStatsQueryRequest request);
}
