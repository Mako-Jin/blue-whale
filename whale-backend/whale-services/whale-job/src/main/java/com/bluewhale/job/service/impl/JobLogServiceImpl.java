package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.converter.JobConverter;
import com.bluewhale.job.dto.JobLogQueryRequest;
import com.bluewhale.job.entity.JobInfo;
import com.bluewhale.job.entity.JobLog;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.mapper.JobLogMapper;
import com.bluewhale.job.scheduler.handler.ExecutorLogClient;
import com.bluewhale.job.service.JobLogService;
import com.bluewhale.job.vo.ExecutorLogVO;
import com.bluewhale.job.vo.JobLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 定时任务执行日志服务实现（写入由调度引擎 {@code JobScheduler} 完成，本服务只读）
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class JobLogServiceImpl implements JobLogService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final JobLogMapper jobLogMapper;

    private final JobInfoMapper jobInfoMapper;

    private final ExecutorLogClient executorLogClient;

    @Override
    public PageResult<JobLogVO> page(JobLogQueryRequest request) {
        LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getJobId() != null, JobLog::getJobId, request.getJobId());
        wrapper.eq(request.getStatus() != null, JobLog::getStatus, request.getStatus());
        wrapper.orderByDesc(JobLog::getStartTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<JobLog> page = jobLogMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<JobLogVO> records = page.getRecords().stream().map(JobLogServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ExecutorLogVO executorLog(Long logId) {
        JobLog jobLog = jobLogMapper.selectById(logId);
        if (jobLog == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "执行流水不存在: " + logId);
        }
        JobInfo job = jobInfoMapper.selectById(jobLog.getJobId());
        ExecutorLogVO vo = new ExecutorLogVO();
        vo.setLogId(logId);
        String jobCode = job == null ? null : job.getJobCode();
        String executorApp = job == null ? null : job.getExecutorApp();
        vo.setJobCode(jobCode);
        vo.setExecutorApp(executorApp);
        if (jobCode == null) {
            vo.setNote("任务定义已删除或未配置 job_code，无法定位执行器日志");
            return vo;
        }
        ExecutorLogClient.Fetched fetched = executorLogClient.fetch(
                job.getTenantId(), executorApp, jobCode, jobLog.getExecutorAddress());
        vo.setAddress(fetched.address());
        vo.setFound(fetched.found());
        vo.setContent(fetched.content());
        vo.setNote(fetched.note());
        return vo;
    }

    /**
     * 执行日志实体转视图对象
     *
     * @param jobLog 执行日志实体
     * @return 执行日志视图对象
     */
    private static JobLogVO toVo(JobLog jobLog) {
        return JobConverter.INSTANCE.toLogVo(jobLog);
    }
}
