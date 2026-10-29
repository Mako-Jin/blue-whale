package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.converter.JobConverter;
import com.bluewhale.job.dto.JobInfoCreateRequest;
import com.bluewhale.job.dto.JobInfoQueryRequest;
import com.bluewhale.job.dto.JobInfoUpdateRequest;
import com.bluewhale.job.entity.JobInfo;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.scheduler.JobHandlerRegistry;
import com.bluewhale.job.scheduler.JobScheduler;
import com.bluewhale.job.service.JobInfoService;
import com.bluewhale.job.vo.JobInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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

    /** 默认重试次数上限：0 表示不重试 */
    private static final int DEFAULT_MAX_RETRY = 0;

    /** 默认重试间隔秒数 */
    private static final int DEFAULT_RETRY_INTERVAL_SECONDS = 60;

    /** 默认分片总数：1 表示不分片 */
    private static final int DEFAULT_SHARD_TOTAL = 1;

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final JobInfoMapper jobInfoMapper;

    private final JobScheduler jobScheduler;

    private final JobHandlerRegistry jobHandlerRegistry;

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
        validateHandler(request.getHandlerBean());
        JobInfo job = JobConverter.INSTANCE.toEntity(request);
        job.setJobGroup(StringUtils.hasText(request.getJobGroup()) ? request.getJobGroup() : DEFAULT_JOB_GROUP);
        job.setMisfirePolicy(StringUtils.hasText(request.getMisfirePolicy())
                ? request.getMisfirePolicy() : DEFAULT_MISFIRE_POLICY);
        job.setMaxRetry(request.getMaxRetry() == null ? DEFAULT_MAX_RETRY : request.getMaxRetry());
        job.setRetryIntervalSeconds(request.getRetryIntervalSeconds() == null
                ? DEFAULT_RETRY_INTERVAL_SECONDS : request.getRetryIntervalSeconds());
        job.setShardTotal(request.getShardTotal() == null ? DEFAULT_SHARD_TOTAL : request.getShardTotal());
        job.setStatus(request.getStatus() == null ? 0 : request.getStatus());
        jobInfoMapper.insert(job);
        jobScheduler.reschedule(job.getId());
        return toVo(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobInfoVO update(Long id, JobInfoUpdateRequest request) {
        validateCron(request.getCronExpression());
        validateHandler(request.getHandlerBean());
        JobInfo job = requireJob(id);
        Integer maxRetry = job.getMaxRetry();
        Integer retryIntervalSeconds = job.getRetryIntervalSeconds();
        Integer shardTotal = job.getShardTotal();
        JobConverter.INSTANCE.updateEntity(request, job);
        if (!StringUtils.hasText(request.getJobGroup())) {
            job.setJobGroup(DEFAULT_JOB_GROUP);
        }
        if (!StringUtils.hasText(request.getMisfirePolicy())) {
            job.setMisfirePolicy(DEFAULT_MISFIRE_POLICY);
        }
        if (request.getMaxRetry() == null) {
            job.setMaxRetry(maxRetry);
        }
        if (request.getRetryIntervalSeconds() == null) {
            job.setRetryIntervalSeconds(retryIntervalSeconds);
        }
        if (request.getShardTotal() == null) {
            job.setShardTotal(shardTotal);
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
     * 校验执行器 Bean 在容器中真实存在且实现 {@link com.bluewhale.job.scheduler.JobHandler}
     *
     * @param handlerBean 执行器 Bean 名称
     * @throws BizException 执行器不存在时抛出 {@code PARAM_ERROR}
     */
    private void validateHandler(String handlerBean) {
        if (!jobHandlerRegistry.exists(handlerBean)) {
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
        JobInfoVO vo = JobConverter.INSTANCE.toInfoVo(job);
        vo.setNextFireTime(resolveNextFireTime(job));
        return vo;
    }

    /**
     * 按 cron 表达式推算下次执行时间
     *
     * @param job 任务实体
     * @return cron 合法时返回下次执行时间，否则返回 {@code null}
     */
    private static LocalDateTime resolveNextFireTime(JobInfo job) {
        String cron = job.getCronExpression();
        if (!StringUtils.hasText(cron) || !CronExpression.isValidExpression(cron)) {
            return null;
        }
        return CronExpression.parse(cron).next(LocalDateTime.now());
    }
}
