package com.bluewhale.job.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.job.dto.JobDependencySetRequest;
import com.bluewhale.job.po.JobDependencyPo;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.mapper.JobDependencyMapper;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.scheduler.JobScheduler;
import com.bluewhale.job.vo.JobDependencyVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobDependencyServiceImpl} 依赖配置校验（存在性 / 自依赖 / 环检测）单元测试。
 *
 * @author BlueWhale
 */
class JobDependencyServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    private final JobDependencyMapper jobDependencyMapper = mock(JobDependencyMapper.class);

    private final JobInfoMapper jobInfoMapper = mock(JobInfoMapper.class);

    private final JobScheduler jobScheduler = mock(JobScheduler.class);

    private final JobDependencyServiceImpl dependencyService =
            new JobDependencyServiceImpl(jobDependencyMapper, jobInfoMapper, jobScheduler);

    private static JobInfoPo job(Long id) {
        JobInfoPo job = new JobInfoPo();
        job.setId(id);
        job.setJobName("任务" + id);
        job.setCronExpression("0 0/5 * * * ?");
        job.setHandlerBean("demoJobHandler");
        job.setStatus(1);
        return job;
    }

    private static JobDependencySetRequest request(Long... upstreamJobIds) {
        JobDependencySetRequest request = new JobDependencySetRequest();
        request.setUpstreamJobIds(Arrays.asList(upstreamJobIds));
        return request;
    }

    @Test
    @DisplayName("保存：任务不存在抛 NOT_FOUND 且不落库")
    void shouldThrowWhenJobMissing() {
        when(jobInfoMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> dependencyService.saveDependencies(99L, request(1L)));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(jobDependencyMapper, never()).purgeByJobId(any());
        verify(jobScheduler, never()).reschedule(any());
    }

    @Test
    @DisplayName("保存：上游任务不存在抛 NOT_FOUND 且不落库")
    void shouldThrowWhenUpstreamMissing() {
        when(jobInfoMapper.selectById(1L)).thenReturn(job(1L));
        when(jobInfoMapper.selectById(2L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> dependencyService.saveDependencies(1L, request(2L)));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(jobDependencyMapper, never()).purgeByJobId(any());
    }

    @Test
    @DisplayName("保存：自依赖抛 PARAM_ERROR")
    void shouldRejectSelfDependency() {
        when(jobInfoMapper.selectById(1L)).thenReturn(job(1L));

        BizException ex = assertThrows(BizException.class,
                () -> dependencyService.saveDependencies(1L, request(1L)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(jobDependencyMapper, never()).purgeByJobId(any());
    }

    @Test
    @DisplayName("保存：新边成环抛 PARAM_ERROR")
    void shouldRejectCycle() {
        when(jobInfoMapper.selectById(1L)).thenReturn(job(1L));
        when(jobInfoMapper.selectById(2L)).thenReturn(job(2L));
        // 任务1 已依赖任务2：任务2 再依赖任务1 即形成 1 → 2 → 1 环
        when(jobDependencyMapper.selectList(any())).thenReturn(List.of(dependency(1L, 2L)));

        BizException ex = assertThrows(BizException.class,
                () -> dependencyService.saveDependencies(2L, request(1L)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(jobDependencyMapper, never()).purgeByJobId(any());
    }

    @Test
    @DisplayName("保存：全量替换、去重并重新调度")
    void shouldReplaceDeduplicateAndReschedule() {
        when(jobInfoMapper.selectById(1L)).thenReturn(job(1L));
        when(jobInfoMapper.selectById(2L)).thenReturn(job(2L));
        when(jobInfoMapper.selectById(3L)).thenReturn(job(3L));
        when(jobDependencyMapper.selectList(any())).thenReturn(List.of());

        JobDependencyVO vo = dependencyService.saveDependencies(1L, request(2L, 2L, 3L));

        verify(jobDependencyMapper).purgeByJobId(1L);
        ArgumentCaptor<JobDependencyPo> captor = ArgumentCaptor.forClass(JobDependencyPo.class);
        verify(jobDependencyMapper, times(2)).insert(captor.capture());
        List<Long> upstreamIds = captor.getAllValues().stream()
                .map(JobDependencyPo::getUpstreamJobId).toList();
        assertEquals(List.of(2L, 3L), upstreamIds);
        assertTrueAllDependenciesBelongTo(captor.getAllValues(), 1L);
        verify(jobScheduler).reschedule(1L);
        assertEquals(1L, vo.getJobId().longValue());
    }

    @Test
    @DisplayName("查询：任务不存在抛 NOT_FOUND")
    void shouldThrowWhenQueryMissing() {
        when(jobInfoMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> dependencyService.getDependencies(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    /**
     * 断言依赖边均属于指定下游任务
     *
     * @param dependencies 依赖边
     * @param expectedJobId 期望的下游任务ID
     */
    private static void assertTrueAllDependenciesBelongTo(List<JobDependencyPo> dependencies, Long expectedJobId) {
        dependencies.forEach(dependency -> assertEquals(expectedJobId, dependency.getJobId()));
    }

    private static JobDependencyPo dependency(Long jobId, Long upstreamJobId) {
        JobDependencyPo dependency = new JobDependencyPo();
        dependency.setJobId(jobId);
        dependency.setUpstreamJobId(upstreamJobId);
        return dependency;
    }
}
