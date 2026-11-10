package com.bluewhale.crm.service.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmServiceProperties;
import com.bluewhale.crm.service.application.command.KbRefCommand;
import com.bluewhale.crm.service.application.view.KbRefView;
import com.bluewhale.crm.service.domain.model.KbRef;
import com.bluewhale.crm.service.domain.port.DocPagePort;
import com.bluewhale.crm.service.domain.repository.KbRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link KbAppServiceImpl} 应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class KbAppServiceImplTest {

    @Mock
    private KbRefRepository kbRefRepository;

    @Mock
    private DocPagePort docPagePort;

    private KbAppServiceImpl service;

    @BeforeEach
    void setUp() {
        CrmProperties properties = new CrmProperties();
        CrmServiceProperties serviceProperties = new CrmServiceProperties();
        service = new KbAppServiceImpl(kbRefRepository, docPagePort, properties, serviceProperties);
    }

    @Test
    @DisplayName("新建：文档可用时写 docAvailable 并取当前版本")
    void shouldMarkDocAvailableWhenDocFound() {
        KbRefCommand command = new KbRefCommand();
        command.setTitle("  设备离线排查  ");
        command.setDocId(100L);
        when(docPagePort.findPublicPage(100L)).thenReturn(Optional.of(
                new DocPagePort.DocPageSnapshot("设备离线排查", 3)));

        KbRefView view = service.create(command, adminActor());

        assertEquals("设备离线排查", view.getTitle());
        assertEquals(100L, view.getDocId());
        assertEquals(3, view.getDocVersion());
        assertTrue(view.getDocAvailable());
        assertEquals("ENABLED", view.getStatus());
        assertEquals(0, view.getHitCount());
    }

    @Test
    @DisplayName("新建：文档服务不可用时降级为不可用，不阻断保存")
    void shouldDegradeWhenDocUnavailable() {
        KbRefCommand command = new KbRefCommand();
        command.setTitle("离线 FAQ");
        command.setDocId(200L);
        when(docPagePort.findPublicPage(200L)).thenReturn(Optional.empty());

        KbRefView view = service.create(command, adminActor());

        assertFalse(view.getDocAvailable());
        assertNull(view.getDocVersion());
    }

    @Test
    @DisplayName("命中：停用的知识引用拒绝计数")
    void shouldRejectHitWhenDisabled() {
        KbRef ref = new KbRef();
        ref.setId(1L);
        ref.setStatus("DISABLED");
        when(kbRefRepository.findById(1L)).thenReturn(ref);

        BizException exception = assertThrows(BizException.class, () -> service.hit(1L, adminActor()));

        assertEquals(CrmErrorCode.PARAM_INVALID, exception.getCode());
    }

    /**
     * 构造管理员操作人。
     *
     * @return 操作人
     */
    private static CrmActor adminActor() {
        return new CrmActor(1L, "admin", Set.of("crm_admin"), Set.of());
    }
}
