package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.dto.JobInfoCreateRequest;
import com.bluewhale.job.dto.JobInfoQueryRequest;
import com.bluewhale.job.dto.JobInfoUpdateRequest;
import com.bluewhale.job.entity.JobInfo;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.scheduler.JobScheduler;
import com.bluewhale.job.service.JobInfoService;
import com.bluewhale.job.vo.JobInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 定时任务信息服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class JobInfoServiceImpl implements JobInfoService {

    /** 任务状态：运行中 */
    private static final int STATUS_RUNNING = 1;

    /** 默认任务分组 */
    private static final String DEFAULT_JOB_GROUP = "DEFAULT";

    /** 默认错过执行策略 */
    private static final String DEFAULT_MISFIRE_POLICY = "SMART";

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final JobInfoMapper jobInfoMapper;

    private final JobScheduler jobScheduler;

    @Override
    public PageResult<JobInfoVO> page(JobInfoQueryRequest request) {
        LambdaQueryWrapper<JobInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getKeyword()), JobInfo::getJobName, request.getKeyword());
        wrapper.eq(StringUtils.hasText(request.getJobGroup()), JobInfo::getJobGroup, request.getJobGroup());
        wrapper.eq(request.getStatus() != null, JobInfo::getStatus, request.getStatus());
        wrapper.orderByDesc(JobInfo::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<JobInfo> page = jobInfoMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<JobInfoVO> records = page.getRecords().stream().map(JobInfoServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public JobInfoVO getDetail(Long id) {
        return toVo(requireJob(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobInfoVO create(JobInfoCreateRequest request) {
        validateCron(request.getCronExpression());
        JobInfo job = new JobInfo();
        BeanUtils.copyProperties(request, job);
        job.setJobGroup(StringUtils.hasText(request.getJobGroup()) ? request.getJobGroup() : DEFAULT_JOB_GROUP);
        job.setMisfirePolicy(StringUtils.hasText(request.getMisfirePolicy())
                ? request.getMisfirePolicy() : DEFAULT_MISFIRE_POLICY);
        job.setStatus(request.getStatus() == null ? 0 : request.getStatus());
        jobInfoMapper.insert(job);
        jobScheduler.reschedule(job.getId());
        return toVo(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobInfoVO update(Long id, JobInfoUpdateRequest request) {
        validateCron(request.getCronExpression());
        JobInfo job = requireJob(id);
        BeanUtils.copyProperties(request, job);
        if (!StringUtils.hasText(request.getJobGroup())) {
            job.setJobGroup(DEFAULT_JOB_GROUP);
        }
        if (!StringUtils.hasText(request.getMisfirePolicy())) {
            job.setMisfirePolicy(DEFAULT_MISFIRE_POLICY);
        }
        if (request.getStatus() == null) {
            job.setStatus(0);
        }
        job.setId(id);
        jobInfoMapper.updateById(job);
        jobScheduler.reschedule(id);
        return toVo(requireJob(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireJob(id);
        jobScheduler.cancel(id);
        jobInfoMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        JobInfo job = requireJob(id);
        job.setStatus(status);
        jobInfoMapper.updateById(job);
        if (Integer.valueOf(STATUS_RUNNING).equals(status)) {
            jobScheduler.reschedule(id);
        } else {
            jobScheduler.cancel(id);
        }
    }

    @Override
    public void runOnce(Long id) {
        jobScheduler.runOnce(id);
    }

    /**
     * 查询任务，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 任务ID
     * @return 任务实体
     */
    private JobInfo requireJob(Long id) {
        JobInfo job = jobInfoMapper.selectById(id);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return job;
    }

    /**
     * 校验 cron 表达式合法性
     *
     * @param cronExpression cron 表达式
     * @throws BizException 表达式非法时抛出 {@code PARAM_ERROR}
     */
    private static void validateCron(String cronExpression) {
        if (!StringUtils.hasText(cronExpression) || !CronExpression.isValidExpression(cronExpression)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    /**
     * 任务实体转视图对象
     *
     * @param job 任务实体
     * @return 任务视图对象
     */
    private static JobInfoVO toVo(JobInfo job) {
        JobInfoVO vo = new JobInfoVO();
        BeanUtils.copyProperties(job, vo);
        return vo;
    }
}
