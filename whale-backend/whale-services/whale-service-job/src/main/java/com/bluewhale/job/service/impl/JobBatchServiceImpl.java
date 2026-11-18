package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.job.dto.JobBatchDeleteRequest;
import com.bluewhale.job.dto.JobBatchStatusRequest;
import com.bluewhale.job.dto.JobDependencySetRequest;
import com.bluewhale.job.dto.JobImportRequest;
import com.bluewhale.job.dto.JobInfoCreateRequest;
import com.bluewhale.job.dto.JobInfoUpdateRequest;
import com.bluewhale.job.dto.JobTransferDependency;
import com.bluewhale.job.dto.JobTransferJob;
import com.bluewhale.job.dto.JobTransferRef;
import com.bluewhale.job.mapper.JobDependencyMapper;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.po.JobDependencyPo;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.service.JobBatchService;
import com.bluewhale.job.service.JobDependencyService;
import com.bluewhale.job.service.JobInfoService;
import com.bluewhale.job.vo.JobBatchFailureVO;
import com.bluewhale.job.vo.JobBatchResultVO;
import com.bluewhale.job.vo.JobExportVO;
import com.bluewhale.job.vo.JobImportFailureVO;
import com.bluewhale.job.vo.JobImportResultVO;
import com.bluewhale.job.vo.JobInfoVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 任务批量操作与导入导出服务实现。
 *
 * <p>批量启停/删除逐条复用 {@link JobInfoService} 的单条方法：存在性校验、启停清零失败计数、
 * 事务提交后注册/取消调度等语义全部保留；单条失败只记入失败明细，不整批回滚。</p>
 *
 * <p>导入按「jobCode 非空用 executorApp + jobCode，否则用 jobName」识别已存在任务，
 * 任务全部落库后再按下游聚合还原依赖，保证同文件内新建的任务可作为依赖端点被解析。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class JobBatchServiceImpl implements JobBatchService {

    /** 导出格式版本 */
    private static final String EXPORT_VERSION = "1.0";

    /** 冲突策略：跳过已存在 */
    private static final String STRATEGY_SKIP = "SKIP";

    /** 冲突策略：覆盖已存在 */
    private static final String STRATEGY_OVERWRITE = "OVERWRITE";

    /** 单次批量操作上限 */
    private static final int MAX_BATCH_SIZE = 200;

    /** 匹配键前缀：按执行器应用名 + 任务编码匹配 */
    private static final String CODE_KEY_PREFIX = "CODE\u0001";

    /** 匹配键前缀：按任务名称匹配 */
    private static final String NAME_KEY_PREFIX = "NAME\u0001";

    private final JobInfoService jobInfoService;

    private final JobDependencyService jobDependencyService;

    private final JobInfoMapper jobInfoMapper;

    private final JobDependencyMapper jobDependencyMapper;

    private final Validator validator;

    @Override
    public JobBatchResultVO batchStatus(JobBatchStatusRequest request) {
        List<Long> jobIds = normalizeIds(request.getJobIds());
        JobBatchResultVO result = new JobBatchResultVO();
        for (Long jobId : jobIds) {
            try {
                jobInfoService.changeStatus(jobId, request.getStatus());
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (RuntimeException e) {
                addBatchFailure(result, jobId, e);
            }
        }
        return result;
    }

    @Override
    public JobBatchResultVO batchDelete(JobBatchDeleteRequest request) {
        List<Long> jobIds = normalizeIds(request.getJobIds());
        JobBatchResultVO result = new JobBatchResultVO();
        for (Long jobId : jobIds) {
            try {
                jobInfoService.delete(jobId);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (RuntimeException e) {
                addBatchFailure(result, jobId, e);
            }
        }
        return result;
    }

    @Override
    public JobExportVO export(String jobGroup) {
        List<JobInfoPo> jobs = jobInfoMapper.selectList(new LambdaQueryWrapper<JobInfoPo>()
                .eq(StringUtils.hasText(jobGroup), JobInfoPo::getJobGroup, jobGroup)
                .orderByAsc(JobInfoPo::getId));
        Map<Long, JobInfoPo> jobById = new LinkedHashMap<>();
        jobs.forEach(job -> jobById.put(job.getId(), job));

        List<JobTransferDependency> dependencies = new ArrayList<>();
        if (!jobById.isEmpty()) {
            jobDependencyMapper.selectList(new LambdaQueryWrapper<JobDependencyPo>()
                            .orderByAsc(JobDependencyPo::getUpstreamJobId)
                            .orderByAsc(JobDependencyPo::getJobId)).stream()
                    .filter(dependency -> jobById.containsKey(dependency.getUpstreamJobId())
                            && jobById.containsKey(dependency.getJobId()))
                    .forEach(dependency -> dependencies.add(toTransferDependency(
                            jobById.get(dependency.getUpstreamJobId()), jobById.get(dependency.getJobId()))));
        }

        JobExportVO vo = new JobExportVO();
        vo.setVersion(EXPORT_VERSION);
        vo.setExportedAt(LocalDateTime.now());
        vo.setJobs(jobs.stream().map(JobBatchServiceImpl::toTransferJob).toList());
        vo.setDependencies(dependencies);
        return vo;
    }

    @Override
    public JobImportResultVO importJobs(JobImportRequest request) {
        String strategy = resolveStrategy(request.getConflictStrategy());
        JobImportResultVO result = new JobImportResultVO();
        Map<String, Long> idByKey = loadJobIndex();
        Map<Long, String> labelById = new HashMap<>();
        List<JobTransferJob> jobs = request.getJobs() == null ? List.of() : request.getJobs();
        for (JobTransferJob item : jobs) {
            if (item == null) {
                continue;
            }
            String target = describeJob(item);
            List<String> violations = validateItem(item);
            if (!violations.isEmpty()) {
                addImportFailure(result, target, String.join("；", violations));
                continue;
            }
            try {
                applyJob(item, strategy, idByKey, labelById, result);
            } catch (RuntimeException e) {
                addImportFailure(result, target, e);
            }
        }
        applyDependencies(request.getDependencies(), idByKey, labelById, result);
        return result;
    }

    /**
     * 导入单个任务：命中匹配键时按策略跳过或覆盖，否则新建
     *
     * @param item 任务定义
     * @param strategy 冲突策略
     * @param idByKey 匹配键 → 任务ID 索引（随导入推进同步更新）
     * @param labelById 任务ID → 展示标识
     * @param result 导入结果
     */
    private void applyJob(JobTransferJob item, String strategy, Map<String, Long> idByKey,
                          Map<Long, String> labelById, JobImportResultVO result) {
        String key = keyOf(item);
        Long existingId = idByKey.get(key);
        if (existingId != null && STRATEGY_SKIP.equals(strategy)) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            labelById.putIfAbsent(existingId, describeJob(item));
            return;
        }
        JobInfoVO saved;
        if (existingId == null) {
            saved = jobInfoService.create(toCreateRequest(item));
            result.setCreatedCount(result.getCreatedCount() + 1);
        } else {
            saved = jobInfoService.update(existingId, toUpdateRequest(item));
            result.setUpdatedCount(result.getUpdatedCount() + 1);
        }
        idByKey.put(key, saved.getId());
        labelById.put(saved.getId(), describeJob(item));
    }

    /**
     * 还原依赖边：按下游聚合后一次性全量替换其上游列表
     *
     * @param dependencies 依赖边列表
     * @param idByKey 匹配键 → 任务ID 索引
     * @param labelById 任务ID → 展示标识
     * @param result 导入结果
     */
    private void applyDependencies(List<JobTransferDependency> dependencies, Map<String, Long> idByKey,
                                   Map<Long, String> labelById, JobImportResultVO result) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }
        Map<Long, Set<Long>> upstreamByDownstream = new LinkedHashMap<>();
        for (JobTransferDependency dependency : dependencies) {
            if (dependency == null) {
                continue;
            }
            Long toId = resolveRef(dependency.getTo(), idByKey);
            Long fromId = resolveRef(dependency.getFrom(), idByKey);
            if (toId == null || fromId == null) {
                String reason = toId == null ? "依赖的下游任务无法解析" : "依赖的上游任务无法解析";
                addImportFailure(result, describeRef(dependency.getTo()), reason);
                continue;
            }
            upstreamByDownstream.computeIfAbsent(toId, key -> new LinkedHashSet<>()).add(fromId);
        }
        upstreamByDownstream.forEach((downstreamId, upstreamIds) -> saveDependencies(
                downstreamId, upstreamIds, labelById, result));
    }

    /**
     * 保存某下游任务的全部上游依赖
     *
     * @param downstreamId 下游任务ID
     * @param upstreamIds 上游任务ID集合
     * @param labelById 任务ID → 展示标识
     * @param result 导入结果
     */
    private void saveDependencies(Long downstreamId, Set<Long> upstreamIds, Map<Long, String> labelById,
                                  JobImportResultVO result) {
        try {
            JobDependencySetRequest request = new JobDependencySetRequest();
            request.setUpstreamJobIds(new ArrayList<>(upstreamIds));
            jobDependencyService.saveDependencies(downstreamId, request);
            result.setDependencyCount(result.getDependencyCount() + upstreamIds.size());
        } catch (RuntimeException e) {
            addImportFailure(result, labelById.getOrDefault(downstreamId, "jobId=" + downstreamId), e);
        }
    }

    /**
     * 读取现有任务并建立匹配索引：编码优先，名称作为兜底
     *
     * @return 匹配键 → 任务ID
     */
    private Map<String, Long> loadJobIndex() {
        List<JobInfoPo> existing = jobInfoMapper.selectList(new LambdaQueryWrapper<JobInfoPo>());
        Map<String, Long> index = new HashMap<>();
        for (JobInfoPo job : existing) {
            if (StringUtils.hasText(job.getJobCode())) {
                index.put(codeKey(job.getExecutorApp(), job.getJobCode()), job.getId());
            }
            if (StringUtils.hasText(job.getJobName())) {
                index.putIfAbsent(nameKey(job.getJobName()), job.getId());
            }
        }
        return index;
    }

    /**
     * 逐条执行 Jakarta Bean Validation，返回全部违规提示
     *
     * <p>导入不在请求体上加 {@code @Valid}：那会让一条坏数据把整份文件 400 拒绝，
     * 与「尽量多导入并报告失败条目」的定位冲突。</p>
     *
     * @param item 任务定义
     * @return 违规提示列表，空列表表示校验通过
     */
    private List<String> validateItem(JobTransferJob item) {
        return validator.validate(item).stream()
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    /**
     * 解析冲突策略，缺省为 SKIP
     *
     * @param conflictStrategy 原始策略文本
     * @return 归一化后的策略
     * @throws BizException 取值不在 SKIP / OVERWRITE 内时抛 {@code PARAM_ERROR}
     */
    private static String resolveStrategy(String conflictStrategy) {
        if (!StringUtils.hasText(conflictStrategy)) {
            return STRATEGY_SKIP;
        }
        String strategy = conflictStrategy.trim().toUpperCase();
        if (!STRATEGY_SKIP.equals(strategy) && !STRATEGY_OVERWRITE.equals(strategy)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "冲突策略只支持 SKIP / OVERWRITE");
        }
        return strategy;
    }

    /**
     * 归一化任务ID列表：过滤空值、按首次出现顺序去重并校验上限
     *
     * @param jobIds 原始任务ID列表
     * @return 去重后的任务ID列表
     * @throws BizException 列表为空或超上限时抛 {@code PARAM_ERROR}
     */
    private static List<Long> normalizeIds(List<Long> jobIds) {
        Set<Long> distinctIds = new LinkedHashSet<>();
        if (jobIds != null) {
            jobIds.stream().filter(Objects::nonNull).forEach(distinctIds::add);
        }
        if (distinctIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "任务ID列表不能为空");
        }
        if (distinctIds.size() > MAX_BATCH_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "单次批量操作不能超过 " + MAX_BATCH_SIZE + " 条");
        }
        return List.copyOf(distinctIds);
    }

    /**
     * 计算任务的匹配键
     *
     * @param item 任务定义
     * @return 匹配键
     */
    private static String keyOf(JobTransferJob item) {
        return StringUtils.hasText(item.getJobCode())
                ? codeKey(item.getExecutorApp(), item.getJobCode())
                : nameKey(item.getJobName());
    }

    /**
     * 由任务引用解析任务ID
     *
     * @param ref 任务引用
     * @param idByKey 匹配键 → 任务ID 索引
     * @return 解析出的任务ID；无法解析返回 {@code null}
     */
    private static Long resolveRef(JobTransferRef ref, Map<String, Long> idByKey) {
        if (ref == null) {
            return null;
        }
        if (StringUtils.hasText(ref.getJobCode())) {
            return idByKey.get(codeKey(ref.getExecutorApp(), ref.getJobCode()));
        }
        if (StringUtils.hasText(ref.getJobName())) {
            return idByKey.get(nameKey(ref.getJobName()));
        }
        return null;
    }

    /**
     * 构造编码匹配键
     *
     * @param executorApp 执行器应用名
     * @param jobCode 任务编码
     * @return 匹配键
     */
    private static String codeKey(String executorApp, String jobCode) {
        return CODE_KEY_PREFIX + nullToEmpty(executorApp) + '\u0001' + nullToEmpty(jobCode);
    }

    /**
     * 构造名称匹配键
     *
     * @param jobName 任务名称
     * @return 匹配键
     */
    private static String nameKey(String jobName) {
        return NAME_KEY_PREFIX + nullToEmpty(jobName);
    }

    /**
     * 空值转空串
     *
     * @param value 原始值
     * @return 非空原值或空串
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 任务描述：优先返回 {@code executorApp:jobCode}，否则返回任务名称
     *
     * @param job 任务定义
     * @return 用于失败明细的展示标识
     */
    private static String describeJob(JobTransferJob job) {
        if (job == null) {
            return "未知任务";
        }
        if (StringUtils.hasText(job.getJobCode())) {
            return nullToEmpty(job.getExecutorApp()) + ":" + job.getJobCode();
        }
        return StringUtils.hasText(job.getJobName()) ? job.getJobName() : "未知任务";
    }

    /**
     * 任务引用描述
     *
     * @param ref 任务引用
     * @return 用于失败明细的展示标识
     */
    private static String describeRef(JobTransferRef ref) {
        if (ref == null) {
            return "未知任务";
        }
        if (StringUtils.hasText(ref.getJobCode())) {
            return nullToEmpty(ref.getExecutorApp()) + ":" + ref.getJobCode();
        }
        return StringUtils.hasText(ref.getJobName()) ? ref.getJobName() : "未知任务";
    }

    /**
     * 记录批量操作失败明细
     *
     * @param result 批量操作结果
     * @param jobId 任务ID
     * @param e 异常
     */
    private static void addBatchFailure(JobBatchResultVO result, Long jobId, RuntimeException e) {
        JobBatchFailureVO failure = new JobBatchFailureVO();
        failure.setId(jobId);
        failure.setReason(describe(e));
        result.getFailures().add(failure);
    }

    /**
     * 记录导入失败明细
     *
     * @param result 导入结果
     * @param target 失败对象标识
     * @param e 异常
     */
    private static void addImportFailure(JobImportResultVO result, String target, RuntimeException e) {
        addImportFailure(result, target, describe(e));
    }

    /**
     * 记录导入失败明细
     *
     * @param result 导入结果
     * @param target 失败对象标识
     * @param reason 失败原因
     */
    private static void addImportFailure(JobImportResultVO result, String target, String reason) {
        JobImportFailureVO failure = new JobImportFailureVO();
        failure.setTarget(target);
        failure.setReason(reason);
        result.getFailures().add(failure);
    }

    /**
     * 提取可读的异常原因
     *
     * @param e 异常
     * @return 业务异常优先取自身消息，否则取异常消息或类型名
     */
    private static String describe(Throwable e) {
        if (e instanceof BizException bizException && StringUtils.hasText(bizException.getMessage())) {
            return bizException.getMessage();
        }
        return StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
    }

    /**
     * 任务实体转导出行
     *
     * @param job 任务实体
     * @return 导出行
     */
    private static JobTransferJob toTransferJob(JobInfoPo job) {
        JobTransferJob item = new JobTransferJob();
        item.setJobName(job.getJobName());
        item.setJobGroup(job.getJobGroup());
        item.setExecutorApp(job.getExecutorApp());
        item.setJobCode(job.getJobCode());
        item.setCronExpression(job.getCronExpression());
        item.setHandlerBean(job.getHandlerBean());
        item.setHandlerParam(job.getHandlerParam());
        item.setMisfirePolicy(job.getMisfirePolicy());
        item.setMaxRetry(job.getMaxRetry());
        item.setRetryIntervalSeconds(job.getRetryIntervalSeconds());
        item.setShardTotal(job.getShardTotal());
        item.setTimeoutSeconds(job.getTimeoutSeconds());
        item.setFailThreshold(job.getFailThreshold());
        item.setStatus(job.getStatus());
        item.setRemark(job.getRemark());
        return item;
    }

    /**
     * 组装一条依赖边
     *
     * @param upstream 上游任务实体
     * @param downstream 下游任务实体
     * @return 依赖边
     */
    private static JobTransferDependency toTransferDependency(JobInfoPo upstream, JobInfoPo downstream) {
        JobTransferDependency dependency = new JobTransferDependency();
        dependency.setFrom(toTransferRef(upstream));
        dependency.setTo(toTransferRef(downstream));
        return dependency;
    }

    /**
     * 任务实体转任务引用
     *
     * @param job 任务实体
     * @return 任务引用
     */
    private static JobTransferRef toTransferRef(JobInfoPo job) {
        JobTransferRef ref = new JobTransferRef();
        ref.setExecutorApp(job.getExecutorApp());
        ref.setJobCode(job.getJobCode());
        ref.setJobName(job.getJobName());
        return ref;
    }

    /**
     * 导出行转创建请求
     *
     * @param item 导出行
     * @return 创建请求
     */
    private static JobInfoCreateRequest toCreateRequest(JobTransferJob item) {
        JobInfoCreateRequest request = new JobInfoCreateRequest();
        request.setJobName(item.getJobName());
        request.setJobGroup(item.getJobGroup());
        request.setExecutorApp(item.getExecutorApp());
        request.setJobCode(item.getJobCode());
        request.setCronExpression(item.getCronExpression());
        request.setHandlerBean(item.getHandlerBean());
        request.setHandlerParam(item.getHandlerParam());
        request.setMisfirePolicy(item.getMisfirePolicy());
        request.setMaxRetry(item.getMaxRetry());
        request.setRetryIntervalSeconds(item.getRetryIntervalSeconds());
        request.setShardTotal(item.getShardTotal());
        request.setTimeoutSeconds(item.getTimeoutSeconds());
        request.setFailThreshold(item.getFailThreshold());
        request.setStatus(item.getStatus());
        request.setRemark(item.getRemark());
        return request;
    }

    /**
     * 导出行转更新请求
     *
     * @param item 导出行
     * @return 更新请求
     */
    private static JobInfoUpdateRequest toUpdateRequest(JobTransferJob item) {
        JobInfoUpdateRequest request = new JobInfoUpdateRequest();
        request.setJobName(item.getJobName());
        request.setJobGroup(item.getJobGroup());
        request.setExecutorApp(item.getExecutorApp());
        request.setJobCode(item.getJobCode());
        request.setCronExpression(item.getCronExpression());
        request.setHandlerBean(item.getHandlerBean());
        request.setHandlerParam(item.getHandlerParam());
        request.setMisfirePolicy(item.getMisfirePolicy());
        request.setMaxRetry(item.getMaxRetry());
        request.setRetryIntervalSeconds(item.getRetryIntervalSeconds());
        request.setShardTotal(item.getShardTotal());
        request.setTimeoutSeconds(item.getTimeoutSeconds());
        request.setFailThreshold(item.getFailThreshold());
        request.setStatus(item.getStatus());
        request.setRemark(item.getRemark());
        return request;
    }
}
