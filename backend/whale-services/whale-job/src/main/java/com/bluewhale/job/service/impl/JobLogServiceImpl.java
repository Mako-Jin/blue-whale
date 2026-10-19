package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.dto.JobLogQueryRequest;
import com.bluewhale.job.entity.JobLog;
import com.bluewhale.job.mapper.JobLogMapper;
import com.bluewhale.job.service.JobLogService;
import com.bluewhale.job.vo.JobLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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

    /**
     * 执行日志实体转视图对象
     *
     * @param jobLog 执行日志实体
     * @return 执行日志视图对象
     */
    private static JobLogVO toVo(JobLog jobLog) {
        JobLogVO vo = new JobLogVO();
        BeanUtils.copyProperties(jobLog, vo);
        return vo;
    }
}
