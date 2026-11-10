package com.bluewhale.oa.document.application.service;

import com.bluewhale.common.context.RequestContext;
import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.document.domain.model.DocAttachment;
import com.bluewhale.oa.document.domain.model.DocIncoming;
import com.bluewhale.oa.document.domain.model.DocOutgoing;
import com.bluewhale.oa.document.domain.model.DocStatus;
import com.bluewhale.oa.document.domain.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 公文读取可见性守卫测试。
 *
 * <p>覆盖发文草稿 / 驳回 / 审核中 / 已签发、秘密级、审批参与人、收文密级、附件归属与类型校验。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class DocReadAccessGuardTest {

    /** 当前用户ID */
    private static final Long USER_ID = 21L;

    /** 当前用户名 */
    private static final String USERNAME = "zhangsan";

    /** 他人用户ID */
    private static final Long OTHER_ID = 99L;

    /** 发文单据ID */
    private static final Long DOC_ID = 1001L;

    /** 附件ID */
    private static final Long ATTACHMENT_ID = 5001L;

    /** 密级：内部 */
    private static final int SECURITY_INTERNAL = 2;

    /** 密级：秘密 */
    private static final int SECURITY_SECRET = 3;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ApprovalInstanceAppService approvalInstanceAppService;

    private DocReadAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DocReadAccessGuard(documentRepository, approvalInstanceAppService);
        RequestContextHolder.set(new RequestContext(USER_ID, USERNAME, 1L, "trace-doc"));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    @DisplayName("可见性：发文草稿对无关用户拒绝")
    void shouldRejectDraftForUnrelatedUser() {
        when(documentRepository.findOutgoing(DOC_ID)).thenReturn(
                outgoing(OTHER_ID, DocStatus.OUTGOING_DRAFT, SECURITY_INTERNAL));
        when(approvalInstanceAppService.isParticipant(any(), anyLong(), eq(USER_ID), eq(USERNAME)))
                .thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> guard.requireReadable(DocStatus.TYPE_OUTGOING, DOC_ID));

        assertEquals(OaErrorCode.DOC_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("可见性：驳回态发文对无关用户拒绝")
    void shouldRejectRejectedOutgoingForUnrelatedUser() {
        when(documentRepository.findOutgoing(DOC_ID)).thenReturn(
                outgoing(OTHER_ID, DocStatus.OUTGOING_REJECTED, SECURITY_INTERNAL));
        when(approvalInstanceAppService.isParticipant(any(), anyLong(), eq(USER_ID), eq(USERNAME)))
                .thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> guard.requireReadable(DocStatus.TYPE_OUTGOING, DOC_ID));

        assertEquals(OaErrorCode.DOC_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("可见性：拟稿人可读自己的草稿")
    void shouldAllowDrafterOfDraft() {
        when(documentRepository.findOutgoing(DOC_ID)).thenReturn(
                outgoing(USER_ID, DocStatus.OUTGOING_DRAFT, SECURITY_INTERNAL));

        guard.requireReadable(DocStatus.TYPE_OUTGOING, DOC_ID);
    }

    @Test
    @DisplayName("可见性：审批参与人可读审核中发文")
    void shouldAllowParticipantOfPendingOutgoing() {
        when(documentRepository.findOutgoing(DOC_ID)).thenReturn(
                outgoing(OTHER_ID, DocStatus.OUTGOING_PENDING, SECURITY_INTERNAL));
        when(approvalInstanceAppService.isParticipant(any(), anyLong(), eq(USER_ID), eq(USERNAME)))
                .thenReturn(true);

        guard.requireReadable(DocStatus.TYPE_OUTGOING, DOC_ID);
    }

    @Test
    @DisplayName("可见性：审核中发文对持权限用户开放，避免流程查询抖动影响审批人")
    void shouldAllowNonParticipantForPendingOutgoing() {
        when(documentRepository.findOutgoing(DOC_ID)).thenReturn(
                outgoing(OTHER_ID, DocStatus.OUTGOING_PENDING, SECURITY_INTERNAL));
        when(approvalInstanceAppService.isParticipant(any(), anyLong(), eq(USER_ID), eq(USERNAME)))
                .thenReturn(false);

        guard.requireReadable(DocStatus.TYPE_OUTGOING, DOC_ID);
    }

    @Test
    @DisplayName("可见性：已签发发文对持权限用户开放")
    void shouldAllowIssuedOutgoing() {
        when(documentRepository.findOutgoing(DOC_ID)).thenReturn(
                outgoing(OTHER_ID, DocStatus.OUTGOING_ISSUED, SECURITY_INTERNAL));
        when(approvalInstanceAppService.isParticipant(any(), anyLong(), eq(USER_ID), eq(USERNAME)))
                .thenReturn(false);

        guard.requireReadable(DocStatus.TYPE_OUTGOING, DOC_ID);
    }

    @Test
    @DisplayName("可见性：秘密级发文即便已下发也仅拟稿人与审批参与人可见")
    void shouldRejectSecretOutgoingEvenIfIssued() {
        when(documentRepository.findOutgoing(DOC_ID)).thenReturn(
                outgoing(OTHER_ID, DocStatus.OUTGOING_ISSUED, SECURITY_SECRET));
        when(approvalInstanceAppService.isParticipant(any(), anyLong(), eq(USER_ID), eq(USERNAME)))
                .thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> guard.requireReadable(DocStatus.TYPE_OUTGOING, DOC_ID));

        assertEquals(OaErrorCode.DOC_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("可见性：秘密级发文的审批参与人可读")
    void shouldAllowParticipantOfSecretOutgoing() {
        when(documentRepository.findOutgoing(DOC_ID)).thenReturn(
                outgoing(OTHER_ID, DocStatus.OUTGOING_PENDING, SECURITY_SECRET));
        when(approvalInstanceAppService.isParticipant(any(), anyLong(), eq(USER_ID), eq(USERNAME)))
                .thenReturn(true);

        guard.requireReadable(DocStatus.TYPE_OUTGOING, DOC_ID);
    }

    @Test
    @DisplayName("可见性：收文登记后对持权限用户开放，秘密级仅承办人可见")
    void shouldApplyIncomingRules() {
        when(documentRepository.findIncoming(DOC_ID)).thenReturn(incoming(OTHER_ID, SECURITY_INTERNAL));
        guard.requireReadable(DocStatus.TYPE_INCOMING, DOC_ID);

        when(documentRepository.findIncoming(DOC_ID)).thenReturn(incoming(OTHER_ID, SECURITY_SECRET));
        BizException exception = assertThrows(BizException.class,
                () -> guard.requireReadable(DocStatus.TYPE_INCOMING, DOC_ID));
        assertEquals(OaErrorCode.DOC_FORBIDDEN, exception.getCode());

        when(documentRepository.findIncoming(DOC_ID)).thenReturn(incoming(USER_ID, SECURITY_SECRET));
        guard.requireReadable(DocStatus.TYPE_INCOMING, DOC_ID);
    }

    @Test
    @DisplayName("附件解析：类型、单据与附件归属全部一致时返回附件")
    void shouldReturnAttachmentWhenOwnershipMatches() {
        DocAttachment attachment = attachment(DocStatus.TYPE_OUTGOING, DOC_ID);
        when(documentRepository.findAttachment(ATTACHMENT_ID)).thenReturn(attachment);
        when(documentRepository.findOutgoing(DOC_ID)).thenReturn(
                outgoing(USER_ID, DocStatus.OUTGOING_DRAFT, SECURITY_INTERNAL));

        assertSame(attachment, guard.requireAttachment(DocStatus.TYPE_OUTGOING, DOC_ID, ATTACHMENT_ID));
    }

    @Test
    @DisplayName("附件解析：附件不存在、单据类型不符或单据ID不符均按公文不存在处理")
    void shouldRejectMismatchedAttachment() {
        when(documentRepository.findAttachment(ATTACHMENT_ID)).thenReturn(
                attachment(DocStatus.TYPE_OUTGOING, DOC_ID + 1));
        BizException mismatch = assertThrows(BizException.class,
                () -> guard.requireAttachment(DocStatus.TYPE_OUTGOING, DOC_ID, ATTACHMENT_ID));
        assertEquals(OaErrorCode.DOC_NOT_FOUND, mismatch.getCode());

        when(documentRepository.findAttachment(ATTACHMENT_ID)).thenReturn(null);
        BizException missing = assertThrows(BizException.class,
                () -> guard.requireAttachment(DocStatus.TYPE_OUTGOING, DOC_ID, ATTACHMENT_ID));
        assertEquals(OaErrorCode.DOC_NOT_FOUND, missing.getCode());

        when(documentRepository.findAttachment(ATTACHMENT_ID)).thenReturn(
                attachment(DocStatus.TYPE_INCOMING, DOC_ID));
        BizException typeMismatch = assertThrows(BizException.class,
                () -> guard.requireAttachment(DocStatus.TYPE_OUTGOING, DOC_ID, ATTACHMENT_ID));
        assertEquals(OaErrorCode.DOC_NOT_FOUND, typeMismatch.getCode());
    }

    @Test
    @DisplayName("可见性：单据不存在按公文不存在处理，非法单据类型按参数非法处理")
    void shouldRejectMissingDocAndUnsupportedType() {
        when(documentRepository.findOutgoing(DOC_ID)).thenReturn(null);
        BizException missing = assertThrows(BizException.class,
                () -> guard.requireReadable(DocStatus.TYPE_OUTGOING, DOC_ID));
        assertEquals(OaErrorCode.DOC_NOT_FOUND, missing.getCode());

        BizException invalidType = assertThrows(BizException.class,
                () -> guard.requireReadable("CONTRACT", DOC_ID));
        assertEquals(OaErrorCode.DOC_INVALID, invalidType.getCode());

        BizException nullDoc = assertThrows(BizException.class,
                () -> guard.requireReadable(DocStatus.TYPE_INCOMING, null));
        assertEquals(OaErrorCode.DOC_NOT_FOUND, nullDoc.getCode());
    }

    /**
     * 构造发文。
     *
     * @param drafterId     拟稿人ID
     * @param status        状态
     * @param securityLevel 密级
     * @return 发文
     */
    private static DocOutgoing outgoing(Long drafterId, String status, int securityLevel) {
        DocOutgoing outgoing = new DocOutgoing();
        outgoing.setId(DOC_ID);
        outgoing.setDrafterId(drafterId);
        outgoing.setStatus(status);
        outgoing.setSecurityLevel(securityLevel);
        return outgoing;
    }

    /**
     * 构造收文。
     *
     * @param assigneeId    承办人ID
     * @param securityLevel 密级
     * @return 收文
     */
    private static DocIncoming incoming(Long assigneeId, int securityLevel) {
        DocIncoming incoming = new DocIncoming();
        incoming.setId(DOC_ID);
        incoming.setAssigneeId(assigneeId);
        incoming.setSecurityLevel(securityLevel);
        incoming.setStatus(DocStatus.INCOMING_REGISTERED);
        return incoming;
    }

    /**
     * 构造公文附件。
     *
     * @param docType 单据类型
     * @param docId   单据ID
     * @return 附件
     */
    private static DocAttachment attachment(String docType, Long docId) {
        DocAttachment attachment = new DocAttachment();
        attachment.setId(ATTACHMENT_ID);
        attachment.setDocType(docType);
        attachment.setDocId(docId);
        attachment.setFileId(9001L);
        attachment.setFileName("通知.pdf");
        return attachment;
    }
}
