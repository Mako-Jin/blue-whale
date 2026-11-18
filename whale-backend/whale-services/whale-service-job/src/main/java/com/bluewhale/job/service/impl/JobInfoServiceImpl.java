package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.job.converter.JobConverter;
import com.bluewhale.job.dto.JobInfoCreateRequest;
import com.bluewhale.job.dto.JobInfoQueryRequest;
import com.bluewhale.job.dto.JobInfoUpdateRequest;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.scheduler.JobHandlerRegistry;
import com.bluewhale.job.scheduler.JobScheduler;
import com.bluewhale.job.service.JobInfoService;
import com.bluewhale.job.vo.JobInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务信息服务实现
 *
 * @author BlueWhale
 */
@Slf4j
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
        LambdaQueryWrapper<JobInfoPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getKeyword()), JobInfoPo::getJobName, request.getKeyword());
        wrapper.eq(StringUtils.hasText(request.getJobGroup()), JobInfoPo::getJobGroup, request.getJobGroup());
        wrapper.eq(request.getStatus() != null, JobInfoPo::getStatus, request.getStatus());
        wrapper.orderByDesc(JobInfoPo::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<JobInfoPo> page = jobInfoMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
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
        JobInfoPo job = JobConverter.INSTANCE.toEntity(request);
        // 防御性显式回填：租户插件关闭时仍能按当前上下文落租户，避免静默落到默认租户
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            job.setTenantId(tenantId);
        }
        job.setJobGroup(StringUtils.hasText(request.getJobGroup()) ? request.getJobGroup() : DEFAULT_JOB_GROUP);
        job.setMisfirePolicy(StringUtils.hasText(request.getMisfirePolicy())
                ? request.getMisfirePolicy() : DEFAULT_MISFIRE_POLICY);
        job.setMaxRetry(request.getMaxRetry() == null ? DEFAULT_MAX_RETRY : request.getMaxRetry());
        job.setRetryIntervalSeconds(request.getRetryIntervalSeconds() == null
                ? DEFAULT_RETRY_INTERVAL_SECONDS : request.getRetryIntervalSeconds());
        job.setShardTotal(request.getShardTotal() == null ? DEFAULT_SHARD_TOTAL : request.getShardTotal());
        job.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 0 : request.getTimeoutSeconds());
        job.setFailThreshold(request.getFailThreshold() == null ? 0 : request.getFailThreshold());
        job.setFailCount(0);
        job.setStatus(request.getStatus() == null ? 0 : request.getStatus());
        jobInfoMapper.insert(job);
        rescheduleAfterCommit(job.getId());
        return toVo(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobInfoVO update(Long id, JobInfoUpdateRequest request) {
        validateCron(request.getCronExpression());
        validateHandler(request.getHandlerBean());
        JobInfoPo job = requireJob(id);
        Integer originalStatus = job.getStatus();
        String originalJobGroup = job.getJobGroup();
        String originalMisfirePolicy = job.getMisfirePolicy();
        Integer maxRetry = job.getMaxRetry();
        Integer retryIntervalSeconds = job.getRetryIntervalSeconds();
        Integer shardTotal = job.getShardTotal();
        Integer timeoutSeconds = job.getTimeoutSeconds();
        Integer failThreshold = job.getFailThreshold();
        Integer failCount = job.getFailCount();
        JobConverter.INSTANCE.updateEntity(request, job);
        if (!StringUtils.hasText(request.getJobGroup())) {
            // 与 maxRetry 等字段保持一致：未传即保留原值，避免编辑时把自定义分组刷回默认
            job.setJobGroup(originalJobGroup);
        }
        if (!StringUtils.hasText(request.getMisfirePolicy())) {
            job.setMisfirePolicy(originalMisfirePolicy);
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
        if (request.getTimeoutSeconds() == null) {
            job.setTimeoutSeconds(timeoutSeconds);
        }
        if (request.getFailThreshold() == null) {
            job.setFailThreshold(failThreshold);
        }
        // 连续失败计数只由调度引擎维护，编辑任务不得覆盖
        job.setFailCount(failCount);
        if (request.getStatus() == null) {
            // 编辑请求通常不带 status（启停走独立接口），未传时必须保留原状态，
            // 否则 MapStruct 会把 null 覆盖进来、任务被静默停止并取消调度
            job.setStatus(originalStatus);
        }
        job.setId(id);
        jobInfoMapper.updateById(job);
        rescheduleAfterCommit(id);
        return toVo(requireJob(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireJob(id);
        jobInfoMapper.deleteById(id);
        cancelAfterCommit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        JobInfoPo job = requireJob(id);
        job.setStatus(status);
        if (Integer.valueOf(STATUS_RUNNING).equals(status)) {
            // 重新启用即视为人工确认恢复：清零失败计数，否则下次失败会立刻再次触发熔断
            job.setFailCount(0);
        }
        jobInfoMapper.updateById(job);
        if (Integer.valueOf(STATUS_RUNNING).equals(status)) {
            rescheduleAfterCommit(id);
        } else {
            cancelAfterCommit(id);
        }
    }

    @Override
    public void runOnce(Long id) {
        jobScheduler.runOnce(id);
    }

    /**
     * 事务提交后重新注册调度。
     *
     * <p>广播必须晚于提交：其他实例收到消息后会回查数据库，提交前回查只能拿到旧值或空行，
     * 会导致新建任务、「停→启」在其余实例上延迟到下一次对账才生效。</p>
     *
     * @param jobId 任务ID
     */
    private void rescheduleAfterCommit(Long jobId) {
        runAfterCommit(() -> jobScheduler.reschedule(jobId), "重新注册调度", jobId);
    }

    /**
     * 事务提交后取消调度
     *
     * @param jobId 任务ID
     */
    private void cancelAfterCommit(Long jobId) {
        runAfterCommit(() -> jobScheduler.cancel(jobId), "取消调度", jobId);
    }

    /**
     * 在事务提交后执行调度动作；无事务时立即执行。
     *
     * <p>提交后调度失败不再回滚已提交的业务数据，只记录告警，由周期对账任务自愈。</p>
     *
     * @param action 调度动作
     * @param actionName 动作名称（日志用）
     * @param jobId 任务ID
     */
    private static void runAfterCommit(Runnable action, String actionName, Long jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invokeQuietly(action, actionName, jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invokeQuietly(action, actionName, jobId);
            }
        });
    }

    /**
     * 执行调度动作并吞掉异常，避免提交成功后向调用方抛出
     *
     * @param action 调度动作
     * @param actionName 动作名称
     * @param jobId 任务ID
     */
    private static void invokeQuietly(Runnable action, String actionName, Long jobId) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("事务提交后{}失败，等待调度对账自愈: jobId={}", actionName, jobId, e);
        }
    }

    /**
     * 查询任务，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 任务ID
     * @return 任务实体
     */
    private JobInfoPo requireJob(Long id) {
        JobInfoPo job = jobInfoMapper.selectById(id);
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
    private static JobInfoVO toVo(JobInfoPo job) {
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
    private static LocalDateTime resolveNextFireTime(JobInfoPo job) {
        String cron = job.getCronExpression();
        if (!StringUtils.hasText(cron) || !CronExpression.isValidExpression(cron)) {
            return null;
        }
        return CronExpression.parse(cron).next(LocalDateTime.now());
    }
}
