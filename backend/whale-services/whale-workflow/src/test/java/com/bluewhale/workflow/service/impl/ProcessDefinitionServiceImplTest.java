package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ProcessDefinitionSaveRequest;
import com.bluewhale.workflow.dto.ProcessQueryRequest;
import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessDefinitionServiceImpl} 流程定义增删改查与分页条件传递单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProcessDefinitionServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    @Mock
    private ProcessDefinitionMapper processDefinitionMapper;

    @InjectMocks
    private ProcessDefinitionServiceImpl processDefinitionService;

    /**
     * 构造流程定义保存请求桩对象。
     *
     * @param code 流程编码
     * @param name 流程名称
     * @return 保存请求
     */
    private static ProcessDefinitionSaveRequest saveRequest(String code, String name) {
        ProcessDefinitionSaveRequest request = new ProcessDefinitionSaveRequest();
        request.setCode(code);
        request.setName(name);
        return request;
    }

    @Test
    @DisplayName("create：编码唯一时落库并返回视图对象（默认版本 / 状态）")
    void shouldCreateWhenCodeUnique() {
        when(processDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<ProcessDefinition>>any())).thenReturn(0L);

        ProcessDefinitionVO vo = processDefinitionService.create(saveRequest("wf-a", "A流程"));

        ArgumentCaptor<ProcessDefinition> captor = ArgumentCaptor.forClass(ProcessDefinition.class);
        verify(processDefinitionMapper).insert(captor.capture());
        assertEquals("wf-a", captor.getValue().getCode());
        assertEquals("A流程", captor.getValue().getName());
        assertEquals(1, captor.getValue().getVersion().intValue());
        assertEquals(1, captor.getValue().getStatus().intValue());
        assertEquals("wf-a", vo.getCode());
    }

    @Test
    @DisplayName("create：编码已存在时抛参数错误且不落库")
    void shouldRejectDuplicateCode() {
        when(processDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<ProcessDefinition>>any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> processDefinitionService.create(saveRequest("wf-a", "A流程")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertEquals("流程编码已存在", ex.getMessage());
        verify(processDefinitionMapper, never()).insert(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("create：definition_json 超过 64KB 抛参数错误且不落库")
    void shouldRejectOversizeDefinition() {
        when(processDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<ProcessDefinition>>any())).thenReturn(0L);
        ProcessDefinitionSaveRequest request = saveRequest("wf-a", "A流程");
        request.setDefinitionJson("x".repeat(65537));

        BizException ex = assertThrows(BizException.class, () -> processDefinitionService.create(request));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(processDefinitionMapper, never()).insert(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("update：存在时更新字段并返回视图对象")
    void shouldUpdateWhenExists() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);
        ProcessDefinitionSaveRequest request = saveRequest("wf-a", "A流程新版");
        request.setDefinitionJson("{\"nodes\":[]}");
        request.setRemark("备注");

        ProcessDefinitionVO vo = processDefinitionService.update(1L, request);

        verify(processDefinitionMapper).updateById(entity);
        assertEquals("A流程新版", entity.getName());
        assertEquals("{\"nodes\":[]}", entity.getDefinitionJson());
        assertEquals("备注", vo.getRemark());
    }

    @Test
    @DisplayName("update：ID 不存在抛 NOT_FOUND 且不更新")
    void shouldThrowWhenUpdateMissing() {
        when(processDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> processDefinitionService.update(99L, saveRequest("wf-a", "A流程")));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(processDefinitionMapper, never()).updateById(any(ProcessDefinition.class));
    }

    @Test
    @DisplayName("delete：ID 不存在抛 NOT_FOUND 且不删除")
    void shouldThrowWhenDeleteMissing() {
        when(processDefinitionMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> processDefinitionService.delete(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(processDefinitionMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("getDetail：存在时返回视图对象")
    void shouldGetDetailWhenExists() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        entity.setName("A流程");
        when(processDefinitionMapper.selectById(1L)).thenReturn(entity);

        ProcessDefinitionVO vo = processDefinitionService.getDetail(1L);

        assertEquals(1L, vo.getId());
        assertEquals("wf-a", vo.getCode());
        assertEquals("A流程", vo.getName());
    }

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        ProcessDefinition entity = new ProcessDefinition();
        entity.setId(1L);
        entity.setCode("wf-a");
        entity.setName("A流程");
        Page<ProcessDefinition> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(processDefinitionMapper.selectPage(ArgumentMatchers.<Page<ProcessDefinition>>any(),
                ArgumentMatchers.<Wrapper<ProcessDefinition>>any())).thenReturn(page);
        ProcessQueryRequest request = new ProcessQueryRequest();
        request.setName("A");
        request.setCode("wf-a");
        request.setStatus(1);

        PageResult<ProcessDefinitionVO> result = processDefinitionService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("wf-a", result.getRecords().get(0).getCode());
        verify(processDefinitionMapper).selectPage(ArgumentMatchers.<Page<ProcessDefinition>>any(),
                ArgumentMatchers.<Wrapper<ProcessDefinition>>any());
    }
}
