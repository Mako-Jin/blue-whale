package com.bluewhale.assistant.service;

import com.bluewhale.assistant.config.AssistantProperties;
import com.bluewhale.assistant.dto.SessionCreateRequest;
import com.bluewhale.assistant.dto.SessionVO;
import com.bluewhale.assistant.entity.AsstDefinition;
import com.bluewhale.assistant.entity.AsstSession;
import com.bluewhale.assistant.mapper.AsstMessageMapper;
import com.bluewhale.assistant.mapper.AsstSessionMapper;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话服务：发布校验、来源白名单、归属隔离与消息上限。
 *
 * @author BlueWhale
 */
class AsstSessionServiceTest {

    private AsstSessionMapper sessionMapper;

    private AsstMessageMapper messageMapper;

    private AsstDefinitionService definitionService;

    private AsstSessionService service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(AsstSessionMapper.class);
        messageMapper = mock(AsstMessageMapper.class);
        definitionService = mock(AsstDefinitionService.class);
        service = new AsstSessionService(sessionMapper, messageMapper, definitionService, new AssistantProperties());
    }

    @Test
    void createRequiresPublishedAssistant() {
        AsstDefinition definition = new AsstDefinition();
        definition.setId(9L);
        definition.setName("MES 助手");
        when(definitionService.requirePublished(9L)).thenReturn(definition);
        when(sessionMapper.insert(any(AsstSession.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AsstSession.class).setId(77L);
            return 1;
        });

        SessionCreateRequest request = new SessionCreateRequest();
        request.setAssistantId(9L);

        SessionVO vo = service.create(request, 7L);

        assertEquals("77", vo.getId());
        assertEquals("platform", vo.getSource());
        assertEquals("MES 助手", vo.getAssistantName());
    }

    @Test
    void createRejectsUnknownSource() {
        AsstDefinition definition = new AsstDefinition();
        definition.setId(9L);
        definition.setName("MES 助手");
        when(definitionService.requirePublished(9L)).thenReturn(definition);

        SessionCreateRequest request = new SessionCreateRequest();
        request.setAssistantId(9L);
        request.setSource("hack");

        assertThrows(BizException.class, () -> service.create(request, 7L));
    }

    @Test
    void requireOwnedRejectsForeignSession() {
        when(sessionMapper.selectById(5L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.requireOwned(5L, 7L));

        AsstSession foreign = new AsstSession();
        foreign.setId(5L);
        foreign.setUserId(8L);
        when(sessionMapper.selectById(5L)).thenReturn(foreign);
        assertThrows(BizException.class, () -> service.requireOwned(5L, 7L));
    }

    @Test
    void appendEnforcesSessionMessageLimit() {
        AssistantProperties properties = new AssistantProperties();
        properties.setSessionMaxMessages(2);
        AsstSessionService limited = new AsstSessionService(
                sessionMapper, messageMapper, definitionService, properties);
        when(messageMapper.selectCount(any())).thenReturn(2L);

        assertThrows(BizException.class,
                () -> limited.append(5L, AsstSessionService.ROLE_USER, "hi", null, 0, 0, 0));
    }

    @Test
    void appendEnforcesMessageByteLimit() {
        AssistantProperties properties = new AssistantProperties();
        properties.setMessageMaxBytes(10);
        AsstSessionService limited = new AsstSessionService(
                sessionMapper, messageMapper, definitionService, properties);

        assertThrows(BizException.class,
                () -> limited.append(5L, AsstSessionService.ROLE_USER, "这条消息远远超过十个字节的限制", null, 0, 0, 0));
    }

    @Test
    void deleteRemovesSessionAndMessages() {
        AsstSession session = new AsstSession();
        session.setId(5L);
        session.setUserId(7L);
        when(sessionMapper.selectById(5L)).thenReturn(session);

        service.delete(5L, 7L);

        verify(sessionMapper).deleteById(5L);
        verify(messageMapper).delete(any());
    }

    @Test
    void titleOfTruncatesLongQuestion() {
        String title = AsstSessionService.titleOf("问".repeat(80));
        assertEquals(50, title.length());
        assertEquals("新会话", AsstSessionService.titleOf("  "));
    }
}
