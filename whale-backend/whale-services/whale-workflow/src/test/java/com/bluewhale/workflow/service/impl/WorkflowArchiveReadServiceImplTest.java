package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.po.ProcessDefinitionVersionPo;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessDefinitionVersionMapper;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.vo.ApprovedInstanceVO;
import com.bluewhale.workflow.vo.ArchiveInstanceDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程实例归档只读服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowArchiveReadServiceImplTest {

    /** 实例更新时间（即办结时间） */
    private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 5, 1, 10, 0);

    @Mock
    private ProcessDefinitionMapper processDefinitionMapper;

    @Mock
    private ProcessDefinitionVersionMapper processDefinitionVersionMapper;

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    @Mock
    private ProcessTaskMapper processTaskMapper;

    private WorkflowArchiveReadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkflowArchiveReadServiceImpl(processDefinitionMapper, processDefinitionVersionMapper,
                processInstanceMapper, processTaskMapper);
    }

    @Test
    @DisplayName("approved：办结时间取更新时间，分页参数收敛到上限")
    void shouldMapApprovedInstancesAndClampPageSize() {
        ProcessInstancePo instance = instance();
        Page<ProcessInstancePo> page = new Page<>(1L, 200L);
        page.setRecords(List.of(instance));
        page.setTotal(1L);
        when(processInstanceMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<ApprovedInstanceVO> result = service.approved(null, null, 0, 1000);

        assertEquals(1L, result.getTotal());
        assertEquals(FINISHED_AT, result.getRecords().get(0).getFinishedAt());
        assertEquals("DOC_APPROVE", result.getRecords().get(0).getDefinitionCode());
        ArgumentCaptor<Page<ProcessInstancePo>> captor = ArgumentCaptor.forClass(Page.class);
        verify(processInstanceMapper).selectPage(captor.capture(), any());
        assertEquals(1L, captor.getValue().getCurrent());
        assertEquals(200L, captor.getValue().getSize());
    }

    @Test
    @DisplayName("detail：返回实例、定义快照与任务轨迹")
    void shouldReturnDetailWithDefinitionAndTasks() {
        when(processInstanceMapper.selectById(7L)).thenReturn(instance());
        when(processDefinitionVersionMapper.selectList(any())).thenReturn(List.of(version()));
        ProcessTaskPo task = new ProcessTaskPo();
        task.setId(1L);
        task.setInstanceId(7L);
        task.setNodeName("部门审批");
        task.setStatus(1);
        when(processTaskMapper.selectList(any())).thenReturn(List.of(task));

        ArchiveInstanceDetailVO detail = service.detail(7L);

        assertEquals("{\"nodes\":[]}", detail.getDefinitionJson());
        assertEquals(FINISHED_AT, detail.getFinishedAt());
        assertEquals(1, detail.getTasks().size());
        assertEquals("部门审批", detail.getTasks().get(0).getNodeName());
    }

    @Test
    @DisplayName("detail：实例不存在时抛业务异常")
    void shouldRejectMissingInstance() {
        when(processInstanceMapper.selectById(7L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.detail(7L));
    }

    /**
     * 构造已通过实例。
     *
     * @return 实例实体
     */
    private static ProcessInstancePo instance() {
        ProcessInstancePo instance = new ProcessInstancePo();
        instance.setId(7L);
        instance.setDefinitionId(3L);
        instance.setDefinitionCode("DOC_APPROVE");
        instance.setDefinitionVersion(2);
        instance.setTitle("出差审批");
        instance.setInitiator("zhangsan");
        instance.setStatus(1);
        instance.setUpdateTime(FINISHED_AT);
        return instance;
    }

    /**
     * 构造定义版本快照。
     *
     * @return 版本快照
     */
    private static ProcessDefinitionVersionPo version() {
        ProcessDefinitionVersionPo version = new ProcessDefinitionVersionPo();
        version.setId(1L);
        version.setDefinitionId(3L);
        version.setVersion(2);
        version.setPublishStatus(1);
        version.setDefinitionJson("{\"nodes\":[]}");
        return version;
    }
}
