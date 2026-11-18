package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.job.dto.JobBatchDeleteRequest;
import com.bluewhale.job.dto.JobBatchStatusRequest;
import com.bluewhale.job.dto.JobDependencySetRequest;
import com.bluewhale.job.dto.JobImportRequest;
import com.bluewhale.job.dto.JobInfoCreateRequest;
import com.bluewhale.job.dto.JobTransferDependency;
import com.bluewhale.job.dto.JobTransferJob;
import com.bluewhale.job.dto.JobTransferRef;
import com.bluewhale.job.mapper.JobDependencyMapper;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.po.JobDependencyPo;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.service.JobDependencyService;
import com.bluewhale.job.service.JobInfoService;
import com.bluewhale.job.vo.JobBatchResultVO;
import com.bluewhale.job.vo.JobExportVO;
import com.bluewhale.job.vo.JobImportResultVO;
import com.bluewhale.job.vo.JobInfoVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobBatchServiceImpl} 单元测试：批量部分失败、导出行结构、导入匹配与依赖还原。
 *
 * @author BlueWhale
 */
class JobBatchServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;

    private final JobInfoService jobInfoService = mock(JobInfoService.class);

    private final JobDependencyService jobDependencyService = mock(JobDependencyService.class);

    private final JobInfoMapper jobInfoMapper = mock(JobInfoMapper.class);

    private final JobDependencyMapper jobDependencyMapper = mock(JobDependencyMapper.class);

    private final Validator validator = mock(Validator.class);

    private final JobBatchServiceImpl service = new JobBatchServiceImpl(jobInfoService, jobDependencyService,
            jobInfoMapper, jobDependencyMapper, validator);

    /**
     * 注册实体元信息缓存：Lambda 条件构造器需要按实体解析列名。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, JobInfoPo.class);
        TableInfoHelper.initTableInfo(assistant, JobDependencyPo.class);
    }

    private static JobInfoPo job(Long id, String executorApp, String jobCode, String jobName) {
        JobInfoPo job = new JobInfoPo();
        job.setId(id);
        job.setJobName(jobName);
        job.setJobGroup("DEFAULT");
        job.setExecutorApp(executorApp);
        job.setJobCode(jobCode);
        job.setCronExpression("0 0/5 * * * ?");
        job.setHandlerBean("demoJobHandler");
        job.setMisfirePolicy("SMART");
        job.setStatus(1);
        return job;
    }

    private static JobInfoVO savedVo(Long id) {
        JobInfoVO vo = new JobInfoVO();
        vo.setId(id);
        return vo;
    }

    private static JobBatchStatusRequest statusRequest(Integer status, Long... jobIds) {
        JobBatchStatusRequest request = new JobBatchStatusRequest();
        request.setJobIds(List.of(jobIds));
        request.setStatus(status);
        return request;
    }

    private static JobBatchDeleteRequest deleteRequest(Long... jobIds) {
        JobBatchDeleteRequest request = new JobBatchDeleteRequest();
        request.setJobIds(List.of(jobIds));
        return request;
    }

    private static JobTransferJob transfer(String jobName, String executorApp, String jobCode) {
        JobTransferJob item = new JobTransferJob();
        item.setJobName(jobName);
        item.setExecutorApp(executorApp);
        item.setJobCode(jobCode);
        item.setCronExpression("0 0/5 * * * ?");
        item.setHandlerBean("demoJobHandler");
        return item;
    }

    private static JobTransferRef ref(String executorApp, String jobCode) {
        JobTransferRef ref = new JobTransferRef();
        ref.setExecutorApp(executorApp);
        ref.setJobCode(jobCode);
        return ref;
    }

    private static JobTransferDependency dependency(JobTransferRef from, JobTransferRef to) {
        JobTransferDependency dependency = new JobTransferDependency();
        dependency.setFrom(from);
        dependency.setTo(to);
        return dependency;
    }

    private static JobImportRequest importRequest(String strategy, List<JobTransferJob> jobs,
                                                  List<JobTransferDependency> dependencies) {
        JobImportRequest request = new JobImportRequest();
        request.setConflictStrategy(strategy);
        request.setJobs(jobs);
        request.setDependencies(dependencies);
        return request;
    }

    @Test
    @DisplayName("批量启停：部分失败只记明细，其余继续执行")
    void shouldAggregateBatchStatusFailures() {
        doThrow(new BizException(ErrorCode.NOT_FOUND)).when(jobInfoService).changeStatus(3L, 0);

        JobBatchResultVO result = service.batchStatus(statusRequest(0, 1L, 2L, 3L));

        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getFailures().size());
        assertEquals(3L, result.getFailures().get(0).getId().longValue());
        assertEquals("资源不存在", result.getFailures().get(0).getReason());
    }

    @Test
    @DisplayName("批量启停：重复 ID 只执行一次")
    void shouldDeduplicateBatchStatusIds() {
        service.batchStatus(statusRequest(1, 1L, 1L));

        verify(jobInfoService, times(1)).changeStatus(1L, 1);
    }

    @Test
    @DisplayName("批量启停：空 ID 列表抛参数错误")
    void shouldRejectEmptyBatchStatusIds() {
        JobBatchStatusRequest request = new JobBatchStatusRequest();
        request.setJobIds(List.of());
        request.setStatus(0);

        BizException ex = assertThrows(BizException.class, () -> service.batchStatus(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(jobInfoService, never()).changeStatus(any(), any());
    }

    @Test
    @DisplayName("批量删除：单条不存在不影响其余删除")
    void shouldAggregateBatchDeleteFailures() {
        doThrow(new BizException(ErrorCode.NOT_FOUND)).when(jobInfoService).delete(2L);

        JobBatchResultVO result = service.batchDelete(deleteRequest(1L, 2L, 3L));

        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getFailures().size());
        assertEquals(2L, result.getFailures().get(0).getId().longValue());
        verify(jobInfoService).delete(1L);
        verify(jobInfoService).delete(3L);
    }

    @Test
    @DisplayName("导出：只保留两端都在集合内的边，并用业务标识表达端点")
    void shouldExportJobsAndInternalEdges() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(
                job(1L, "whale-crm", "crm.order-pull", "订单拉取"),
                job(2L, "whale-crm", "crm.order-push", "订单下发")));
        when(jobDependencyMapper.selectList(any())).thenReturn(List.of(
                dependencyPo(1L, 2L), dependencyPo(2L, 3L)));

        JobExportVO vo = service.export(null);

        assertEquals("1.0", vo.getVersion());
        assertNotNull(vo.getExportedAt());
        assertEquals(2, vo.getJobs().size());
        assertEquals(1, vo.getDependencies().size());
        assertEquals("crm.order-pull", vo.getDependencies().get(0).getFrom().getJobCode());
        assertEquals("crm.order-push", vo.getDependencies().get(0).getTo().getJobCode());
    }

    @Test
    @DisplayName("导入 SKIP：已存在任务被跳过且不写入")
    void shouldSkipExistingJobOnImport() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(1L, "whale-crm", "crm.order-push", "订单下发")));

        JobImportResultVO result = service.importJobs(importRequest("SKIP",
                List.of(transfer("订单下发", "whale-crm", "crm.order-push")), List.of()));

        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getCreatedCount());
        verify(jobInfoService, never()).create(any());
        verify(jobInfoService, never()).update(any(), any());
    }

    @Test
    @DisplayName("导入 OVERWRITE：已存在任务按导入内容更新")
    void shouldOverwriteExistingJobOnImport() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(5L, "whale-crm", "crm.order-push", "订单下发")));
        when(jobInfoService.update(eq(5L), any())).thenReturn(savedVo(5L));

        JobImportResultVO result = service.importJobs(importRequest("OVERWRITE",
                List.of(transfer("订单下发", "whale-crm", "crm.order-push")), List.of()));

        assertEquals(1, result.getUpdatedCount());
        verify(jobInfoService).update(eq(5L), any());
        verify(jobInfoService, never()).create(any());
    }

    @Test
    @DisplayName("导入：无匹配键（编码为空）时按任务名称匹配")
    void shouldMatchByNameWhenJobCodeBlank() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(7L, null, null, "无编码任务")));

        JobImportResultVO result = service.importJobs(importRequest("SKIP",
                List.of(transfer("无编码任务", null, null)), List.of()));

        assertEquals(1, result.getSkippedCount());
        verify(jobInfoService, never()).create(any());
    }

    @Test
    @DisplayName("导入：新任务按顺序新建，返回创建计数")
    void shouldCreateNewJobsOnImport() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of());
        when(jobInfoService.create(any())).thenAnswer(invocation -> {
            JobInfoCreateRequest request = invocation.getArgument(0);
            return savedVo("订单下发".equals(request.getJobName()) ? 11L : 10L);
        });

        JobImportResultVO result = service.importJobs(importRequest("SKIP", List.of(
                transfer("订单拉取", "whale-crm", "crm.order-pull"),
                transfer("订单下发", "whale-crm", "crm.order-push")), List.of()));

        assertEquals(2, result.getCreatedCount());
        assertEquals(0, result.getFailures().size());
    }

    @Test
    @DisplayName("导入依赖：同一下游的多条上游合并为一次全量替换")
    void shouldApplyDependenciesGroupedByDownstream() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of());
        Map<String, Long> ids = Map.of("订单拉取", 10L, "订单下发", 11L, "库存同步", 12L);
        when(jobInfoService.create(any())).thenAnswer(invocation -> {
            JobInfoCreateRequest request = invocation.getArgument(0);
            return savedVo(ids.get(request.getJobName()));
        });

        JobImportResultVO result = service.importJobs(importRequest("SKIP", List.of(
                transfer("订单拉取", "whale-crm", "crm.order-pull"),
                transfer("订单下发", "whale-crm", "crm.order-push"),
                transfer("库存同步", "whale-crm", "crm.stock-sync")), List.of(
                dependency(ref("whale-crm", "crm.order-pull"), ref("whale-crm", "crm.order-push")),
                dependency(ref("whale-crm", "crm.stock-sync"), ref("whale-crm", "crm.order-push")))));

        ArgumentCaptor<JobDependencySetRequest> captor = ArgumentCaptor.forClass(JobDependencySetRequest.class);
        verify(jobDependencyService).saveDependencies(eq(11L), captor.capture());
        assertEquals(List.of(10L, 12L), captor.getValue().getUpstreamJobIds());
        assertEquals(2, result.getDependencyCount());
        assertEquals(0, result.getFailures().size());
    }

    @Test
    @DisplayName("导入依赖：端点无法解析记失败明细且不保存依赖")
    void shouldReportUnresolvedDependencyEndpoint() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of());
        when(jobInfoService.create(any())).thenReturn(savedVo(10L));

        JobImportResultVO result = service.importJobs(importRequest("SKIP",
                List.of(transfer("订单下发", "whale-crm", "crm.order-push")), List.of(
                dependency(ref("whale-crm", "crm.unknown"), ref("whale-crm", "crm.order-push")))));

        assertEquals(0, result.getDependencyCount());
        assertEquals(1, result.getFailures().size());
        assertEquals("whale-crm:crm.order-push", result.getFailures().get(0).getTarget());
        assertEquals("依赖的上游任务无法解析", result.getFailures().get(0).getReason());
        verify(jobDependencyService, never()).saveDependencies(any(), any());
    }

    @Test
    @DisplayName("导入：非法冲突策略直接抛参数错误且不读取任务")
    void shouldRejectUnknownConflictStrategy() {
        JobImportRequest request = importRequest("UNKNOWN", List.of(), List.of());

        BizException ex = assertThrows(BizException.class, () -> service.importJobs(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(jobInfoMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("导入：单条字段校验失败只记录该条，不影响其余任务")
    void shouldReportValidationFailurePerItem() {
        ConstraintViolation<JobTransferJob> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("任务名称不能为空");
        when(validator.validate(ArgumentMatchers.any(JobTransferJob.class))).thenReturn(Set.of(violation));
        when(jobInfoMapper.selectList(any())).thenReturn(List.of());

        JobImportResultVO result = service.importJobs(importRequest("SKIP",
                List.of(transfer(null, "whale-crm", "crm.order-push")), List.of()));

        assertEquals(0, result.getCreatedCount());
        assertEquals(1, result.getFailures().size());
        assertEquals("任务名称不能为空", result.getFailures().get(0).getReason());
        verify(jobInfoService, never()).create(any());
    }

    /**
     * 构造依赖实体
     *
     * @param upstreamId 上游任务ID
     * @param jobId 下游任务ID
     * @return 依赖实体
     */
    private static JobDependencyPo dependencyPo(Long upstreamId, Long jobId) {
        JobDependencyPo dependency = new JobDependencyPo();
        dependency.setUpstreamJobId(upstreamId);
        dependency.setJobId(jobId);
        return dependency;
    }
}
