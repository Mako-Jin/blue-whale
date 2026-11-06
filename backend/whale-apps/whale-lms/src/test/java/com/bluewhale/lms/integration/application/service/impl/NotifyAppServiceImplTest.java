package com.bluewhale.lms.integration.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.integration.domain.gateway.NotifyGateway;
import com.bluewhale.lms.integration.domain.model.NotifyLog;
import com.bluewhale.lms.integration.domain.model.NotifyLogQuery;
import com.bluewhale.lms.integration.domain.model.NotifyScene;
import com.bluewhale.lms.integration.domain.model.NotifyStatus;
import com.bluewhale.lms.integration.domain.repository.NotifyLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知投递：成功留痕、失败可重试、已发不重发。
 *
 * @author BlueWhale
 */
class NotifyAppServiceImplTest {

    /** 幂等键 */
    private static final String KEY = "AUTO_URGE:9:20261106";

    /** 接收人 */
    private static final long USER_ID = 100L;

    private NotifyLogRepository notifyLogRepository;

    private NotifyGateway notifyGateway;

    private NotifyAppServiceImpl service;

    @BeforeEach
    void setUp() {
        notifyLogRepository = mock(NotifyLogRepository.class);
        notifyGateway = mock(NotifyGateway.class);
        LmsProperties properties = new LmsProperties();
        properties.setDemandGatePassed(true);
        properties.setNotifyPageLimit(100);
        service = new NotifyAppServiceImpl(notifyLogRepository, notifyGateway, properties);
    }

    /**
     * 首次投递写 SENT 留痕。
     */
    @Test
    @DisplayName("首次投递成功写成功留痕")
    void firstDeliveryMarksSent() {
        when(notifyLogRepository.findByKeyAndUser(KEY, USER_ID)).thenReturn(Optional.empty());
        when(notifyLogRepository.save(any(NotifyLog.class))).thenReturn(1L);

        int notified = service.notifyUrge(9L, "安全生产培训", List.of(USER_ID), LocalDate.of(2026, 11, 6));

        assertEquals(1, notified);
        ArgumentCaptor<NotifyLog> captor = ArgumentCaptor.forClass(NotifyLog.class);
        verify(notifyLogRepository).save(captor.capture());
        NotifyLog saved = captor.getValue();
        assertEquals(KEY, saved.getIdempotencyKey());
        assertEquals(NotifyScene.URGE, saved.getScene());
        assertEquals(NotifyStatus.SENT, saved.getSendStatus());
        assertEquals(USER_ID, saved.getUserId());
    }

    /**
     * 已成功的接收人直接跳过，不再打消息服务。
     */
    @Test
    @DisplayName("已成功的接收人不重复投递")
    void sentRecipientIsSkipped() {
        NotifyLog sent = new NotifyLog();
        sent.setId(5L);
        sent.setUserId(USER_ID);
        sent.setScene(NotifyScene.URGE);
        sent.setIdempotencyKey(KEY);
        sent.setSendStatus(NotifyStatus.SENT);
        when(notifyLogRepository.findByKeyAndUser(KEY, USER_ID)).thenReturn(Optional.of(sent));

        int notified = service.notifyUrge(9L, "安全生产培训", List.of(USER_ID), LocalDate.of(2026, 11, 6));

        assertEquals(0, notified);
        verify(notifyGateway, never()).send(any());
        verify(notifyLogRepository, never()).save(any(NotifyLog.class));
    }

    /**
     * 失败记录会被重试覆盖为成功，而不是永久漏发。
     */
    @Test
    @DisplayName("失败记录可被重试覆盖为成功")
    void failedRecipientIsRetried() {
        NotifyLog failed = new NotifyLog();
        failed.setId(6L);
        failed.setUserId(USER_ID);
        failed.setScene(NotifyScene.URGE);
        failed.setIdempotencyKey(KEY);
        failed.setSendStatus(NotifyStatus.FAILED);
        failed.setFailReason("消息服务不可用");
        when(notifyLogRepository.findByKeyAndUser(KEY, USER_ID)).thenReturn(Optional.of(failed));
        when(notifyLogRepository.save(any(NotifyLog.class))).thenReturn(6L);

        int notified = service.notifyUrge(9L, "安全生产培训", List.of(USER_ID), LocalDate.of(2026, 11, 6));

        assertEquals(1, notified);
        assertEquals(NotifyStatus.SENT, failed.getSendStatus());
        assertEquals(null, failed.getFailReason());
    }

    /**
     * 投递失败先留痕再抛错。
     */
    @Test
    @DisplayName("投递失败留痕并上抛")
    void deliveryFailureMarksFailed() {
        when(notifyLogRepository.findByKeyAndUser(KEY, USER_ID)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("消息服务超时")).when(notifyGateway).send(any());

        BizException exception = assertThrows(BizException.class,
                () -> service.notifyUrge(9L, "安全生产培训", List.of(USER_ID), LocalDate.of(2026, 11, 6)));
        assertEquals(LmsErrorCode.NOTIFY_FAILED, exception.getCode());

        ArgumentCaptor<NotifyLog> captor = ArgumentCaptor.forClass(NotifyLog.class);
        verify(notifyLogRepository).save(captor.capture());
        assertEquals(NotifyStatus.FAILED, captor.getValue().getSendStatus());
        assertEquals("消息服务超时", captor.getValue().getFailReason());
    }

    /**
     * 附带通知失败不抛，主流程继续。
     */
    @Test
    @DisplayName("附带通知失败不影响主流程")
    void quietSendSwallowsFailure() {
        when(notifyLogRepository.findByKeyAndUser(anyString(), any())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("消息服务超时")).when(notifyGateway).send(any());

        int notified = service.notifyCertIssue(11L, "CERT-2026-000001", "安全生产证书", USER_ID);

        assertEquals(0, notified);
    }

    /**
     * 多接收人逐个留痕。
     */
    @Test
    @DisplayName("多接收人逐个留痕")
    void multipleRecipientsRecordedIndividually() {
        when(notifyLogRepository.findByKeyAndUser(anyString(), any())).thenReturn(Optional.empty());
        when(notifyLogRepository.save(any(NotifyLog.class))).thenReturn(1L);

        int notified = service.notifyUrge(9L, "安全生产培训", List.of(1L, 2L, 2L), LocalDate.of(2026, 11, 6));

        assertEquals(2, notified);
        verify(notifyLogRepository, times(2)).save(any(NotifyLog.class));
        verify(notifyGateway, times(1)).send(any());
    }

    /**
     * 门禁关闭后不再发送。
     */
    @Test
    @DisplayName("门禁关闭拒绝发送")
    void gateClosedRejectsSend() {
        LmsProperties closed = new LmsProperties();
        closed.setDemandGatePassed(false);
        NotifyAppServiceImpl gated = new NotifyAppServiceImpl(notifyLogRepository, notifyGateway, closed);

        BizException exception = assertThrows(BizException.class,
                () -> gated.send(List.of(USER_ID), NotifyScene.URGE, KEY, "TRAINING_CLASS", 9L, "标题", "正文"));
        assertEquals(LmsErrorCode.GATE_CLOSED, exception.getCode());
    }

    /**
     * 日志查询仅管理员可看。
     */
    @Test
    @DisplayName("非管理员不可查看通知日志")
    void pageRejectsNonAdmin() {
        NotifyLogQuery query = new NotifyLogQuery();
        BizException exception = assertThrows(BizException.class,
                () -> service.page(query, new LmsActor(9L, "member", Set.of("lms_member"), Set.of())));
        assertEquals(LmsErrorCode.NOTIFY_INVALID, exception.getCode());
    }
}
