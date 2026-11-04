package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.workflow.dto.UrgeRequest;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.entity.ProcessUrge;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.mapper.ProcessUrgeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessUrgeServiceImpl} 催办频控与归属校验测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProcessUrgeServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    @Mock
    private ProcessUrgeMapper processUrgeMapper;

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @InjectMocks
    private ProcessUrgeServiceImpl processUrgeService;

    @Test
    @DisplayName("urge：首次催办落库")
    void shouldInsertWhenFirstUrge() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(1L);
        when(processInstanceMapper.selectById(1L)).thenReturn(instance);
        when(processUrgeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        processUrgeService.urge(1L, request(null));

        ArgumentCaptor<ProcessUrge> captor = ArgumentCaptor.forClass(ProcessUrge.class);
        verify(processUrgeMapper).insert(captor.capture());
        assertEquals("zhangsan", captor.getValue().getUrger());
        assertEquals(1L, captor.getValue().getInstanceId());
    }

    @Test
    @DisplayName("urge：1 小时内二次催办抛 400 且不落库")
    void shouldThrowWhenUrgeTooFrequent() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(1L);
        when(processInstanceMapper.selectById(1L)).thenReturn(instance);
        when(processUrgeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> processUrgeService.urge(1L, request(null)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(processUrgeMapper, never()).insert(any(ProcessUrge.class));
    }

    @Test
    @DisplayName("urge：实例不存在抛 404")
    void shouldThrowWhenInstanceMissing() {
        BizException ex = assertThrows(BizException.class, () -> processUrgeService.urge(1L, request(null)));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("urge：任务不属于实例抛 400")
    void shouldThrowWhenTaskNotBelongToInstance() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(1L);
        ProcessTask task = new ProcessTask();
        task.setInstanceId(2L);
        when(processInstanceMapper.selectById(1L)).thenReturn(instance);
        when(processTaskMapper.selectById(9L)).thenReturn(task);

        BizException ex = assertThrows(BizException.class, () -> processUrgeService.urge(1L, request(9L)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    /**
     * 构造催办请求。
     *
     * @param taskId 任务ID
     * @return 催办请求
     */
    private static UrgeRequest request(Long taskId) {
        UrgeRequest request = new UrgeRequest();
        request.setTaskId(taskId);
        request.setUrger("zhangsan");
        request.setComment("请尽快处理");
        return request;
    }
}
