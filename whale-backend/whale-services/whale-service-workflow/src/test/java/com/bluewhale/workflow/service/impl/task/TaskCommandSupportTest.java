package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.support.WorkflowConstants;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TaskCommandSupport} 并行活动节点 CAS 更新单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TaskCommandSupportTest {

    @Mock
    private ProcessTaskMapper processTaskMapper;

    @Mock
    private ProcessInstanceMapper processInstanceMapper;

    private TaskCommandSupport support;

    /**
     * 初始化实体表元数据，供 LambdaUpdateWrapper 解析列名
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProcessInstancePo.class);
    }

    @BeforeEach
    void setUp() {
        support = new TaskCommandSupport(processTaskMapper, processInstanceMapper);
    }

    /**
     * 构造运行中的实例桩对象。
     *
     * @param id 实例ID
     * @param activeNodeKeys 活动节点键
     * @return 实例实体
     */
    private static ProcessInstancePo instance(Long id, String activeNodeKeys) {
        ProcessInstancePo instance = new ProcessInstancePo();
        instance.setId(id);
        instance.setStatus(WorkflowConstants.INSTANCE_RUNNING);
        instance.setActiveNodeKeys(activeNodeKeys);
        return instance;
    }

    @Test
    @DisplayName("removeActiveKey：无并行分支时直接返回空串且不更新实例")
    void shouldSkipUpdateWhenNoActiveKeys() {
        ProcessInstancePo instance = instance(5L, null);

        assertEquals("", support.removeActiveKey(instance, "n1"));
        verify(processInstanceMapper, never()).update(ArgumentMatchers.<ProcessInstancePo>isNull(),
                ArgumentMatchers.<Wrapper<ProcessInstancePo>>any());
    }

    @Test
    @DisplayName("removeActiveKey：CAS 成功返回剩余键并同步实例字段")
    void shouldReturnRemainingWhenCasSucceeds() {
        ProcessInstancePo instance = instance(5L, "n1,n2");
        when(processInstanceMapper.update(ArgumentMatchers.<ProcessInstancePo>isNull(),
                ArgumentMatchers.<Wrapper<ProcessInstancePo>>any())).thenReturn(1);

        assertEquals("n2", support.removeActiveKey(instance, "n1"));
        assertEquals("n2", instance.getActiveNodeKeys());
    }

    @Test
    @DisplayName("removeActiveKey：CAS 冲突后重读实例并重试成功")
    void shouldRetryAfterConflict() {
        ProcessInstancePo instance = instance(5L, "n1,n2");
        when(processInstanceMapper.update(ArgumentMatchers.<ProcessInstancePo>isNull(),
                ArgumentMatchers.<Wrapper<ProcessInstancePo>>any())).thenReturn(0, 1);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance(5L, "n1,n3"));

        assertEquals("n3", support.removeActiveKey(instance, "n1"));
        assertEquals("n3", instance.getActiveNodeKeys());
    }

    @Test
    @DisplayName("removeActiveKey：两次冲突抛参数错误")
    void shouldThrowWhenStillConflicting() {
        ProcessInstancePo instance = instance(5L, "n1,n2");
        when(processInstanceMapper.update(ArgumentMatchers.<ProcessInstancePo>isNull(),
                ArgumentMatchers.<Wrapper<ProcessInstancePo>>any())).thenReturn(0);
        when(processInstanceMapper.selectById(5L)).thenReturn(instance(5L, "n1,n2"));

        BizException ex = assertThrows(BizException.class, () -> support.removeActiveKey(instance, "n1"));

        assertEquals(400, ex.getCode());
        assertEquals("流程实例状态已变更，请重试", ex.getMessage());
    }
}
