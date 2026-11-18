package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.dto.JobInfoCreateRequest;
import com.bluewhale.job.dto.JobInfoQueryRequest;
import com.bluewhale.job.dto.JobInfoUpdateRequest;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.scheduler.JobHandlerRegistry;
import com.bluewhale.job.scheduler.JobScheduler;
import com.bluewhale.job.vo.JobInfoVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobInfoServiceImpl} 任务增删改查、执行器校验与下次执行时间推算单元测试。
 *
 * @author BlueWhale
 */
class JobInfoServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    private final JobInfoMapper jobInfoMapper = mock(JobInfoMapper.class);

    private final JobScheduler jobScheduler = mock(JobScheduler.class);

    private final JobHandlerRegistry jobHandlerRegistry = mock(JobHandlerRegistry.class);

    private final JobInfoServiceImpl jobInfoService =
            new JobInfoServiceImpl(jobInfoMapper, jobScheduler, jobHandlerRegistry);

    private static JobInfoPo job(Long id, Integer status) {
        JobInfoPo job = new JobInfoPo();
        job.setId(id);
        job.setJobName("任务A");
        job.setJobGroup("DEFAULT");
        job.setCronExpression("0 0/5 * * * ?");
        job.setHandlerBean("demoJobHandler");
        job.setMisfirePolicy("SMART");
        job.setStatus(status);
        return job;
    }

    private static JobInfoCreateRequest createRequest() {
        JobInfoCreateRequest request = new JobInfoCreateRequest();
        request.setJobName("任务A");
        request.setCronExpression("0 0/5 * * * ?");
        request.setHandlerBean("demoJobHandler");
        return request;
    }

    private static JobInfoUpdateRequest updateRequest() {
        JobInfoUpdateRequest request = new JobInfoUpdateRequest();
        request.setJobName("任务A");
        request.setCronExpression("0 0/5 * * * ?");
        request.setHandlerBean("demoJobHandler");
        return request;
    }

    @Test
    @DisplayName("create：默认分组/策略/状态并回填 nextFireTime")
    void shouldCreateWithDefaults() {
        when(jobHandlerRegistry.exists("demoJobHandler")).thenReturn(true);

        JobInfoVO vo = jobInfoService.create(createRequest());

        assertEquals("DEFAULT", vo.getJobGroup());
        assertEquals("SMART", vo.getMisfirePolicy());
        assertEquals(0, vo.getStatus().intValue());
        assertNotNull(vo.getNextFireTime());
        verify(jobInfoMapper).insert(any(JobInfoPo.class));
        verify(jobScheduler).reschedule(any());
    }

    @Test
    @DisplayName("create：cron 非法抛参数错误且不落库")
    void shouldRejectInvalidCronOnCreate() {
        JobInfoCreateRequest request = createRequest();
        request.setCronExpression("bad-cron");

        BizException ex = assertThrows(BizException.class, () -> jobInfoService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(jobInfoMapper, never()).insert(any(JobInfoPo.class));
    }

    @Test
    @DisplayName("create：执行器不存在抛参数错误且不落库")
    void shouldRejectUnknownHandlerOnCreate() {
        when(jobHandlerRegistry.exists("demoJobHandler")).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> jobInfoService.create(createRequest()));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(jobInfoMapper, never()).insert(any(JobInfoPo.class));
    }

    @Test
    @DisplayName("update：更新后按最新定义重新调度")
    void shouldUpdateAndReschedule() {
        when(jobHandlerRegistry.exists("demoJobHandler")).thenReturn(true);
        when(jobInfoMapper.selectById(1L)).thenReturn(job(1L, 1));

        JobInfoVO vo = jobInfoService.update(1L, updateRequest());

        assertEquals(1L, vo.getId().longValue());
        assertNotNull(vo.getNextFireTime());
        verify(jobInfoMapper).updateById(any(JobInfoPo.class));
        verify(jobScheduler).reschedule(1L);
    }

    @Test
    @DisplayName("update：请求未传 status 时保留原状态，不静默停止任务")
    void shouldPreserveStatusWhenUpdateWithoutStatus() {
        when(jobHandlerRegistry.exists("demoJobHandler")).thenReturn(true);
        when(jobInfoMapper.selectById(1L)).thenReturn(job(1L, 1));

        jobInfoService.update(1L, updateRequest());

        ArgumentCaptor<JobInfoPo> captor = ArgumentCaptor.forClass(JobInfoPo.class);
        verify(jobInfoMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getStatus().intValue());
        verify(jobScheduler).reschedule(1L);
    }

    @Test
    @DisplayName("update：任务不存在抛 NOT_FOUND")
    void shouldThrowWhenUpdateMissing() {
        when(jobHandlerRegistry.exists("demoJobHandler")).thenReturn(true);
        when(jobInfoMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> jobInfoService.update(99L, updateRequest()));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(jobInfoMapper, never()).updateById(any(JobInfoPo.class));
    }

    @Test
    @DisplayName("delete：先取消调度再执行删除")
    void shouldCancelThenDelete() {
        when(jobInfoMapper.selectById(1L)).thenReturn(job(1L, 1));

        jobInfoService.delete(1L);

        verify(jobScheduler).cancel(1L);
        verify(jobInfoMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete：任务不存在抛 NOT_FOUND 且不删除")
    void shouldThrowWhenDeleteMissing() {
        when(jobInfoMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> jobInfoService.delete(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(jobScheduler, never()).cancel(99L);
        verify(jobInfoMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    @DisplayName("changeStatus：启动后重新注册调度")
    void shouldRescheduleWhenEnabled() {
        when(jobInfoMapper.selectById(1L)).thenReturn(job(1L, 0));

        jobInfoService.changeStatus(1L, 1);

        verify(jobInfoMapper).updateById(any(JobInfoPo.class));
        verify(jobScheduler).reschedule(1L);
        verify(jobScheduler, never()).cancel(1L);
    }

    @Test
    @DisplayName("changeStatus：停止后取消调度")
    void shouldCancelWhenDisabled() {
        when(jobInfoMapper.selectById(1L)).thenReturn(job(1L, 1));

        jobInfoService.changeStatus(1L, 0);

        verify(jobInfoMapper).updateById(any(JobInfoPo.class));
        verify(jobScheduler).cancel(1L);
        verify(jobScheduler, never()).reschedule(1L);
    }

    @Test
    @DisplayName("runOnce：委托调度引擎立即执行")
    void shouldDelegateRunOnce() {
        jobInfoService.runOnce(1L);

        verify(jobScheduler).runOnce(1L);
    }

    @Test
    @DisplayName("page：返回分页结果并回填 nextFireTime")
    void shouldPageWithNextFireTime() {
        Page<JobInfoPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(job(1L, 1)));
        page.setTotal(1L);
        when(jobInfoMapper.selectPage(ArgumentMatchers.<IPage<JobInfoPo>>any(),
                ArgumentMatchers.<Wrapper<JobInfoPo>>any())).thenReturn(page);

        PageResult<JobInfoVO> result = jobInfoService.page(new JobInfoQueryRequest());

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getNextFireTime());
    }

    @Test
    @DisplayName("getDetail：任务不存在抛 NOT_FOUND")
    void shouldThrowWhenDetailMissing() {
        when(jobInfoMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> jobInfoService.getDetail(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("getDetail：cron 非法时 nextFireTime 为空")
    void shouldReturnNullNextFireTimeWhenCronInvalid() {
        JobInfoPo job = job(1L, 0);
        job.setCronExpression(null);
        when(jobInfoMapper.selectById(1L)).thenReturn(job);

        JobInfoVO vo = jobInfoService.getDetail(1L);

        assertNull(vo.getNextFireTime());
    }
}
