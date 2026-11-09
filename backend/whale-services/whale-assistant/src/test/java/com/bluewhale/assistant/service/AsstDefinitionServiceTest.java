package com.bluewhale.assistant.service;

import com.bluewhale.assistant.dto.DefinitionSaveRequest;
import com.bluewhale.assistant.dto.DefinitionVO;
import com.bluewhale.assistant.entity.AsstDefinition;
import com.bluewhale.assistant.entity.AsstToolGrant;
import com.bluewhale.assistant.mapper.AsstDefinitionMapper;
import com.bluewhale.assistant.mapper.AsstToolGrantMapper;
import com.bluewhale.assistant.support.ToolRegistryClient;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 助手定义服务：编码唯一、模型参数白名单、状态机与工具授权覆盖。
 *
 * @author BlueWhale
 */
class AsstDefinitionServiceTest {

    private AsstDefinitionMapper definitionMapper;

    private AsstToolGrantMapper toolGrantMapper;

    private ToolRegistryClient toolRegistryClient;

    private AsstDefinitionService service;

    @BeforeEach
    void setUp() {
        definitionMapper = mock(AsstDefinitionMapper.class);
        toolGrantMapper = mock(AsstToolGrantMapper.class);
        toolRegistryClient = mock(ToolRegistryClient.class);
        service = new AsstDefinitionService(definitionMapper, toolGrantMapper, toolRegistryClient);
    }

    @Test
    void createRejectsDuplicateCode() {
        when(definitionMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.create(request(), 7L));
    }

    @Test
    void createNormalizesModelParamsAndStartsAsDraft() {
        when(definitionMapper.selectCount(any())).thenReturn(0L);
        DefinitionSaveRequest request = request();
        request.setModelParams(Map.of("model", "gpt-4o-mini", "temperature", 0.3, "max_tokens", 256));

        DefinitionVO vo = service.create(request, 7L);

        assertEquals("draft", vo.getStatus());
        assertEquals("7", vo.getCreatedBy());
        assertNotNull(vo.getModelParams());
        assertEquals("gpt-4o-mini", vo.getModelParams().get("model"));
    }

    @Test
    void createRejectsUnknownModelParamKey() {
        when(definitionMapper.selectCount(any())).thenReturn(0L);
        DefinitionSaveRequest request = request();
        request.setModelParams(Map.of("apiKey", "leak"));

        assertThrows(BizException.class, () -> service.create(request, 7L));
    }

    @Test
    void requirePublishedRejectsDraft() {
        AsstDefinition draft = definition(1L, AsstDefinitionService.STATUS_DRAFT);
        when(definitionMapper.selectById(1L)).thenReturn(draft);

        assertThrows(BizException.class, () -> service.requirePublished(1L));
    }

    @Test
    void requirePublishedAcceptsPublished() {
        when(definitionMapper.selectById(1L)).thenReturn(definition(1L, AsstDefinitionService.STATUS_PUBLISHED));

        assertNotNull(service.requirePublished(1L));
    }

    @Test
    void publishAndDisableTransitionStatus() {
        when(definitionMapper.selectById(1L)).thenReturn(definition(1L, AsstDefinitionService.STATUS_DRAFT));

        assertEquals(AsstDefinitionService.STATUS_PUBLISHED, service.publish(1L).getStatus());
        assertEquals(AsstDefinitionService.STATUS_DISABLED, service.disable(1L).getStatus());
    }

    @Test
    void setToolsReplacesWhitelistWithUpsert() throws Exception {
        when(definitionMapper.selectById(1L)).thenReturn(definition(1L, AsstDefinitionService.STATUS_PUBLISHED));
        when(toolRegistryClient.listToolNames())
                .thenReturn(Set.of("knowledge_search", "fact_get", "doc_read"));
        AsstToolGrant existing = new AsstToolGrant();
        existing.setId(10L);
        existing.setAssistantId(1L);
        existing.setToolCode("knowledge_search");
        existing.setGranted(1);
        when(toolGrantMapper.selectList(any())).thenReturn(List.of(existing));

        service.setTools(1L, List.of("fact_get"), 7L);

        // 旧授权被关闭
        assertEquals(0, existing.getGranted());
        verify(toolGrantMapper).updateById(existing);
        // 新授权插入
        verify(toolGrantMapper).insert(any(AsstToolGrant.class));
        verify(toolGrantMapper, never()).delete(any());
    }

    @Test
    void setToolsRejectsUnknownToolCode() throws Exception {
        when(definitionMapper.selectById(1L)).thenReturn(definition(1L, AsstDefinitionService.STATUS_PUBLISHED));
        when(toolRegistryClient.listToolNames()).thenReturn(Set.of("knowledge_search", "fact_get"));

        BizException exception = assertThrows(BizException.class,
                () -> service.setTools(1L, List.of("knowledge_search", "data_dump"), 7L));

        assertTrue(exception.getMessage().contains("data_dump"));
        verify(toolGrantMapper, never()).insert(any(AsstToolGrant.class));
    }

    @Test
    void setToolsFailsClosedWhenRegistryUnavailable() throws Exception {
        when(definitionMapper.selectById(1L)).thenReturn(definition(1L, AsstDefinitionService.STATUS_PUBLISHED));
        when(toolRegistryClient.listToolNames()).thenThrow(new IOException("connection refused"));

        assertThrows(BizException.class, () -> service.setTools(1L, List.of("knowledge_search"), 7L));
        verify(toolGrantMapper, never()).insert(any(AsstToolGrant.class));
    }

    @Test
    void requirePublishedByCodeRejectsDraft() {
        when(definitionMapper.selectOne(any())).thenReturn(definition(1L, AsstDefinitionService.STATUS_DRAFT));

        assertThrows(BizException.class, () -> service.requirePublishedByCode("platform-helper"));
    }

    @Test
    void requirePublishedByCodeAcceptsPublished() {
        when(definitionMapper.selectOne(any()))
                .thenReturn(definition(1L, AsstDefinitionService.STATUS_PUBLISHED));

        assertNotNull(service.requirePublishedByCode("platform-helper"));
    }

    @Test
    void getReturnsVoWithWhitelist() {
        when(definitionMapper.selectById(1L)).thenReturn(definition(1L, AsstDefinitionService.STATUS_PUBLISHED));
        AsstToolGrant grant = new AsstToolGrant();
        grant.setAssistantId(1L);
        grant.setToolCode("fact_get");
        grant.setGranted(1);
        when(toolGrantMapper.selectList(any())).thenReturn(List.of(grant));

        DefinitionVO vo = service.get(1L);

        assertEquals("platform-helper", vo.getCode());
        assertTrue(vo.getToolWhitelist().contains("fact_get"));
        assertNull(vo.getModelParams());
    }

    private static DefinitionSaveRequest request() {
        DefinitionSaveRequest request = new DefinitionSaveRequest();
        request.setCode("platform-helper");
        request.setName("平台助手");
        request.setSystemPrompt("你是蓝鲸平台助手。");
        return request;
    }

    private static AsstDefinition definition(Long id, String status) {
        AsstDefinition definition = new AsstDefinition();
        definition.setId(id);
        definition.setCode("platform-helper");
        definition.setName("平台助手");
        definition.setStatus(status);
        return definition;
    }
}
