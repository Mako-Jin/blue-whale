package com.bluewhale.pdm.change.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pdm.change.client.PdmChgMessageClient;
import com.bluewhale.pdm.change.dto.PdmChgNoticeSaveRequest;
import com.bluewhale.pdm.change.entity.PdmChgNotice;
import com.bluewhale.pdm.change.entity.PdmChgNotifyLog;
import com.bluewhale.pdm.change.entity.PdmChgOrder;
import com.bluewhale.pdm.change.entity.PdmChgTask;
import com.bluewhale.pdm.change.mapper.PdmChgNoticeMapper;
import com.bluewhale.pdm.change.mapper.PdmChgNotifyLogMapper;
import com.bluewhale.pdm.change.mapper.PdmChgOrderMapper;
import com.bluewhale.pdm.change.mapper.PdmChgTaskMapper;
import com.bluewhale.pdm.change.support.PdmChgNoticeStatus;
import com.bluewhale.pdm.change.support.PdmChgObjectType;
import com.bluewhale.pdm.change.support.PdmChgOrderStatus;
import com.bluewhale.pdm.change.support.PdmChgTaskStatus;
import com.bluewhale.pdm.change.support.PdmChgTraceRecorder;
import com.bluewhale.pdm.change.vo.PdmChgNoticeVO;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.mapper.PdmDocVersionMapper;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变更通知服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmChgNoticeServiceImplTest {

    /** 通知ID */
    private static final Long NOTICE_ID = 3L;

    /** 变更单ID */
    private static final Long ECO_ID = 8L;

    @Mock
    private PdmChgNoticeMapper noticeMapper;

    @Mock
    private PdmChgOrderMapper chgOrderMapper;

    @Mock
    private PdmChgTaskMapper chgTaskMapper;

    @Mock
    private PdmChgNotifyLogMapper notifyLogMapper;

    @Mock
    private PdmDocVersionMapper docVersionMapper;

    @Mock
    private PdmBomMapper bomMapper;

    @Mock
    private PdmChgMessageClient messageClient;

    @Mock
    private PdmChgTraceRecorder traceRecorder;

    @InjectMocks
    private PdmChgNoticeServiceImpl noticeService;

    /**
     * 初始化 MyBatis-Plus 表元数据。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PdmChgNotice.class);
        TableInfoHelper.initTableInfo(assistant, PdmChgNotifyLog.class);
        TableInfoHelper.initTableInfo(assistant, PdmChgTask.class);
        TableInfoHelper.initTableInfo(assistant, PdmDocVersion.class);
        TableInfoHelper.initTableInfo(assistant, PdmBom.class);
    }

    /**
     * 未审批通过的变更单不能创建通知。
     */
    @Test
    void createShouldRejectUnapprovedOrder() {
        when(chgOrderMapper.selectById(ECO_ID)).thenReturn(order(PdmChgOrderStatus.APPROVING));

        assertThrows(BizException.class, () -> noticeService.create(saveRequest(), 1L));

        verify(noticeMapper, never()).insert(any(PdmChgNotice.class));
    }

    /**
     * 生效日期早于审批通过日被拒。
     */
    @Test
    void createShouldRejectEffectiveBeforeApprove() {
        PdmChgOrder order = order(PdmChgOrderStatus.APPROVED);
        order.setApproveTime(LocalDateTime.now().plusDays(1));
        when(chgOrderMapper.selectById(ECO_ID)).thenReturn(order);

        BizException ex = assertThrows(BizException.class, () -> noticeService.create(saveRequest(), 1L));

        assertTrue(ex.getMessage().contains("生效日期不能早于审批通过日"));
        verify(noticeMapper, never()).insert(any(PdmChgNotice.class));
    }

    /**
     * 生效日期未到不允许提前生效。
     */
    @Test
    void effectiveShouldRejectBeforeEffectiveFrom() {
        PdmChgNotice notice = notice(PdmChgNoticeStatus.NOTIFIED);
        notice.setEffectiveFrom(LocalDateTime.now().plusDays(1));
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(notice);

        BizException ex = assertThrows(BizException.class, () -> noticeService.effective(NOTICE_ID, 1L));

        assertTrue(ex.getMessage().contains("不能提前生效"));
        verify(noticeMapper, never()).updateById(any(PdmChgNotice.class));
    }

    /**
     * 已生效重复调用幂等，不再更新。
     */
    @Test
    void effectiveShouldBeIdempotent() {
        PdmChgNotice notice = notice(PdmChgNoticeStatus.EFFECTIVE);
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(notice);
        when(chgOrderMapper.selectById(ECO_ID)).thenReturn(order(PdmChgOrderStatus.APPROVED));

        PdmChgNoticeVO vo = noticeService.effective(NOTICE_ID, 1L);

        assertEquals(PdmChgNoticeStatus.EFFECTIVE, vo.getStatus());
        verify(noticeMapper, never()).updateById(any(PdmChgNotice.class));
        verify(messageClient, never()).send(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any());
    }

    /**
     * 通知失败不回滚生效事实，失败落投递日志。
     */
    @Test
    void effectiveShouldCommitWhenNotifyFails() {
        PdmChgNotice notice = notice(PdmChgNoticeStatus.NOTIFIED);
        notice.setEffectiveFrom(LocalDateTime.now().minusHours(1));
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(notice);
        when(chgOrderMapper.selectById(ECO_ID)).thenReturn(order(PdmChgOrderStatus.APPROVED));
        when(chgTaskMapper.selectList(any())).thenReturn(List.of());
        doThrow(new RuntimeException("消息服务不可用")).when(messageClient)
                .send(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());

        noticeService.effective(NOTICE_ID, 1L);

        ArgumentCaptor<PdmChgNotice> noticeCaptor = ArgumentCaptor.forClass(PdmChgNotice.class);
        verify(noticeMapper).updateById(noticeCaptor.capture());
        assertEquals(PdmChgNoticeStatus.EFFECTIVE, noticeCaptor.getValue().getStatus());
        ArgumentCaptor<PdmChgNotifyLog> logCaptor = ArgumentCaptor.forClass(PdmChgNotifyLog.class);
        verify(notifyLogMapper).insert(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
    }

    /**
     * 生效时把目标文档的旧版本置为作废。
     */
    @Test
    void effectiveShouldObsoleteOlderDocumentVersions() {
        PdmChgNotice notice = notice(PdmChgNoticeStatus.NOTIFIED);
        notice.setEffectiveFrom(LocalDateTime.now().minusHours(1));
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(notice);
        when(chgOrderMapper.selectById(ECO_ID)).thenReturn(order(PdmChgOrderStatus.APPROVED));

        PdmChgTask task = new PdmChgTask();
        task.setEcoId(ECO_ID);
        task.setStatus(PdmChgTaskStatus.COMPLETED);
        task.setTargetType(PdmChgObjectType.DOCUMENT);
        task.setTargetId(100L);
        task.setResultVersionNo("V2.0");
        when(chgTaskMapper.selectList(any())).thenReturn(List.of(task));

        PdmDocVersion target = new PdmDocVersion();
        target.setDocumentId(100L);
        target.setVersionNo("V2.0");
        target.setVersionSeq(2);
        target.setStatus("RELEASED");
        when(docVersionMapper.selectOne(any())).thenReturn(target);

        PdmDocVersion older = new PdmDocVersion();
        older.setDocumentId(100L);
        older.setVersionNo("V1.0");
        older.setVersionSeq(1);
        older.setStatus("RELEASED");
        when(docVersionMapper.selectList(any())).thenReturn(List.of(older));

        noticeService.effective(NOTICE_ID, 1L);

        ArgumentCaptor<PdmDocVersion> captor = ArgumentCaptor.forClass(PdmDocVersion.class);
        verify(docVersionMapper).updateById(captor.capture());
        assertEquals("OBSOLETE", captor.getValue().getStatus());
    }

    /**
     * 无效通知ID报参数错误。
     */
    @Test
    void detailShouldRejectMissingNotice() {
        when(noticeMapper.selectById(anyLong())).thenReturn(null);

        assertThrows(BizException.class, () -> noticeService.detail(NOTICE_ID));
    }

    /**
     * 重试成功把失败日志更新为成功。
     */
    @Test
    void retryNotifyShouldMarkLogSuccess() {
        PdmChgNotice notice = notice(PdmChgNoticeStatus.NOTIFIED);
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(notice);
        PdmChgNotifyLog failed = new PdmChgNotifyLog();
        failed.setId(11L);
        failed.setNoticeId(NOTICE_ID);
        failed.setIdempotencyKey("ECN:3:ECN_NOTIFIED");
        failed.setTitle("变更生效通知");
        failed.setContent("请按新版本执行");
        failed.setStatus("FAILED");
        failed.setRetryCount(0);
        when(notifyLogMapper.selectList(any())).thenReturn(List.of(failed));
        when(chgOrderMapper.selectById(ECO_ID)).thenReturn(order(PdmChgOrderStatus.APPROVED));

        noticeService.retryNotify(NOTICE_ID, 1L);

        ArgumentCaptor<PdmChgNotifyLog> captor = ArgumentCaptor.forClass(PdmChgNotifyLog.class);
        verify(notifyLogMapper).updateById(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
    }

    /**
     * 重试失败保留失败状态并抛错。
     */
    @Test
    void retryNotifyShouldKeepFailedState() {
        PdmChgNotice notice = notice(PdmChgNoticeStatus.NOTIFIED);
        when(noticeMapper.selectById(NOTICE_ID)).thenReturn(notice);
        PdmChgNotifyLog failed = new PdmChgNotifyLog();
        failed.setId(11L);
        failed.setNoticeId(NOTICE_ID);
        failed.setIdempotencyKey("ECN:3:ECN_NOTIFIED");
        failed.setStatus("FAILED");
        failed.setRetryCount(1);
        when(notifyLogMapper.selectList(any())).thenReturn(List.of(failed));
        doThrow(new RuntimeException("消息服务不可用")).when(messageClient)
                .send(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());

        assertThrows(BizException.class, () -> noticeService.retryNotify(NOTICE_ID, 1L));

        ArgumentCaptor<PdmChgNotifyLog> captor = ArgumentCaptor.forClass(PdmChgNotifyLog.class);
        verify(notifyLogMapper).updateById(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
    }

    /**
     * 构造保存请求。
     */
    private static PdmChgNoticeSaveRequest saveRequest() {
        PdmChgNoticeSaveRequest request = new PdmChgNoticeSaveRequest();
        request.setEcoId(ECO_ID);
        request.setTitle("变更生效通知");
        request.setContent("请按新版本执行");
        request.setNotifyTargets(List.of(1L, 2L));
        request.setNotifyChannels(List.of("INBOX"));
        request.setEffectiveFrom(LocalDateTime.now().plusHours(1));
        return request;
    }

    /**
     * 构造变更单。
     */
    private static PdmChgOrder order(String status) {
        PdmChgOrder order = new PdmChgOrder();
        order.setId(ECO_ID);
        order.setEcoNo("EO-1");
        order.setStatus(status);
        order.setApproveTime(LocalDateTime.now().minusDays(1));
        order.setImpactJson("[]");
        return order;
    }

    /**
     * 构造通知。
     */
    private static PdmChgNotice notice(String status) {
        PdmChgNotice notice = new PdmChgNotice();
        notice.setId(NOTICE_ID);
        notice.setNoticeNo("CN-1");
        notice.setEcoId(ECO_ID);
        notice.setTitle("变更生效通知");
        notice.setContent("请按新版本执行");
        notice.setNotifyTargets("[1,2]");
        notice.setNotifyChannels("INBOX");
        notice.setEffectiveFrom(LocalDateTime.now().minusHours(1));
        notice.setStatus(status);
        return notice;
    }
}
