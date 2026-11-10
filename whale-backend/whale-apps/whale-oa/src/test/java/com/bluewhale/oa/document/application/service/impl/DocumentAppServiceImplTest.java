package com.bluewhale.oa.document.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.AttachmentOwnershipGuard;
import com.bluewhale.oa.common.domain.repository.SequenceRepository;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.service.DocArchiveAppService;
import com.bluewhale.oa.document.application.service.DocNoAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.gateway.ArchiveGateway;
import com.bluewhale.oa.document.domain.gateway.ArchivePushCommand;
import com.bluewhale.oa.document.domain.model.DocArchiveRef;
import com.bluewhale.oa.document.domain.model.DocIncoming;
import com.bluewhale.oa.document.domain.model.DocNoRule;
import com.bluewhale.oa.document.domain.model.DocOutgoing;
import com.bluewhale.oa.document.domain.model.DocStatus;
import com.bluewhale.oa.document.domain.model.DocTemplate;
import com.bluewhale.oa.document.domain.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公文应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class DocumentAppServiceImplTest {

    private static final Long DOC_ID = 11L;

    private static final Long USER_ID = 8L;

    private static final String USERNAME = "张三";

    @Mock
    private DocumentRepository repository;

    @Mock
    private SequenceRepository sequenceRepository;

    @Mock
    private DocNoAppService docNoAppService;

    @Mock
    private DocArchiveAppService docArchiveAppService;

    @Mock
    private ApprovalInstanceAppService approvalInstanceAppService;

    @Mock
    private ArchiveGateway archiveGateway;

    @Mock
    private AttachmentOwnershipGuard attachmentOwnershipGuard;

    private DocTemplateAppServiceImpl templateAppService;

    private DocNoAppServiceImpl docNoService;

    private DocOutgoingAppServiceImpl outgoingAppService;

    private DocIncomingAppServiceImpl incomingAppService;

    private DocArchiveAppServiceImpl archiveAppService;

    @BeforeEach
    void setUp() {
        templateAppService = new DocTemplateAppServiceImpl(repository);
        docNoService = new DocNoAppServiceImpl(repository, sequenceRepository);
        outgoingAppService = new DocOutgoingAppServiceImpl(
                repository, docNoAppService, docArchiveAppService, approvalInstanceAppService,
                attachmentOwnershipGuard);
        incomingAppService = new DocIncomingAppServiceImpl(repository, docNoAppService,
                docArchiveAppService, attachmentOwnershipGuard);
        archiveAppService = new DocArchiveAppServiceImpl(repository, archiveGateway, new ObjectMapper());
    }

    @Test
    @DisplayName("文号：按年 + 三位补零")
    void shouldGenerateYearlyDocNo() {
        DocNoRule rule = rule("OUT_DEFAULT", "YEAR", "BW-OUT-", 3, "-");
        when(repository.findRuleByCode("OUT_DEFAULT")).thenReturn(rule);
        when(sequenceRepository.nextValue("DOC:OUT_DEFAULT", String.valueOf(LocalDate.now().getYear()))
        ).thenReturn(7L);

        String docNo = docNoService.nextNo("OUT_DEFAULT");

        assertEquals("BW-OUT-" + LocalDate.now().getYear() + "-007", docNo);
    }

    @Test
    @DisplayName("文号：规则缺失时回落默认格式")
    void shouldFallbackWhenRuleMissing() {
        when(repository.findRuleByCode(anyString())).thenReturn(null);
        when(sequenceRepository.nextValue(eq("DOC:FALLBACK"), anyString())).thenReturn(1L);

        String docNo = docNoService.nextNo("MISSING");

        String expectedPeriod = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        assertEquals("BW-DOC-" + expectedPeriod + "-001", docNo);
    }

    @Test
    @DisplayName("模板：同编码同版本拒绝")
    void shouldRejectDuplicateTemplate() {
        when(repository.templateExists("TPL", 1, null)).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> templateAppService.create(templateCommand()));

        assertEquals(OaErrorCode.DOC_TEMPLATE_EXISTS, exception.getCode());
        verify(repository, never()).saveTemplate(any());
    }

    @Test
    @DisplayName("发文：拟稿分配文号并保存附件")
    void shouldCreateOutgoingDraft() {
        when(docNoAppService.nextNo("OUT_DEFAULT")).thenReturn("BW-OUT-2026-001");

        DocumentViews.Outgoing view = outgoingAppService.create(outgoingCommand(), USER_ID, USERNAME);

        assertNotNull(view.getDocNo());
        assertEquals(DocStatus.OUTGOING_DRAFT, view.getStatus());
        verify(repository).saveOutgoing(any(DocOutgoing.class));
        verify(repository).saveAttachment(any());
    }

    @Test
    @DisplayName("发文：未审核通过不可签发")
    void shouldRejectSignBeforeApproved() {
        DocOutgoing outgoing = outgoing(DocStatus.OUTGOING_PENDING);
        when(repository.findOutgoing(DOC_ID)).thenReturn(outgoing);

        DocumentCommands.OutgoingSign command = new DocumentCommands.OutgoingSign();
        BizException exception = assertThrows(BizException.class,
                () -> outgoingAppService.sign(DOC_ID, command));

        assertEquals(OaErrorCode.DOC_STATUS_CONFLICT, exception.getCode());
        verify(repository, never()).updateOutgoing(any());
    }

    @Test
    @DisplayName("发文：提交登记审批实例")
    void shouldRegisterApprovalOnSubmit() {
        DocOutgoing outgoing = outgoing(DocStatus.OUTGOING_DRAFT);
        when(repository.findOutgoing(DOC_ID)).thenReturn(outgoing);
        ApprovalInstanceView instance = new ApprovalInstanceView();
        instance.setId(99L);
        when(approvalInstanceAppService.register(any())).thenReturn(instance);

        DocumentViews.Outgoing view = outgoingAppService.submit(DOC_ID);

        assertEquals(DocStatus.OUTGOING_PENDING, view.getStatus());
        assertEquals(99L, view.getApprovalInstanceId());
        verify(approvalInstanceAppService).register(any());
        verify(repository, times(2)).updateOutgoing(any());
    }

    @Test
    @DisplayName("发文：审批结果只回写审核中的单据")
    void shouldSyncApprovalResultOnlyWhenPending() {
        DocOutgoing outgoing = outgoing(DocStatus.OUTGOING_APPROVED);
        when(repository.findOutgoing(DOC_ID)).thenReturn(outgoing);

        outgoingAppService.syncApprovalResult(DOC_ID, "REJECTED");

        assertEquals(DocStatus.OUTGOING_APPROVED, outgoing.getStatus());
        verify(repository, never()).updateOutgoing(any());
    }

    @Test
    @DisplayName("归档：已归档幂等，不再推送")
    void shouldSkipArchiveWhenAlreadyArchived() {
        DocOutgoing outgoing = outgoing(DocStatus.OUTGOING_ARCHIVED);
        when(repository.findOutgoing(DOC_ID)).thenReturn(outgoing);
        DocArchiveRef ref = archiveRef(DocStatus.ARCHIVE_ARCHIVED, "ITEM-1");
        when(repository.findArchiveRef(DocStatus.TYPE_OUTGOING, DOC_ID)).thenReturn(ref);

        String itemId = archiveAppService.archive(DocStatus.TYPE_OUTGOING, DOC_ID);

        assertEquals("ITEM-1", itemId);
        verify(archiveGateway, never()).archive(any());
    }

    @Test
    @DisplayName("归档：成功后回写单据与映射")
    void shouldArchiveAndUpdateDoc() {
        DocOutgoing outgoing = outgoing(DocStatus.OUTGOING_ISSUED);
        when(repository.findOutgoing(DOC_ID)).thenReturn(outgoing);
        when(repository.findArchiveRef(DocStatus.TYPE_OUTGOING, DOC_ID)).thenReturn(null);
        when(archiveGateway.archive(any())).thenReturn("ITEM-2");

        String itemId = archiveAppService.archive(DocStatus.TYPE_OUTGOING, DOC_ID);

        assertEquals("ITEM-2", itemId);
        assertEquals(DocStatus.OUTGOING_ARCHIVED, outgoing.getStatus());
        assertEquals("ITEM-2", outgoing.getArchiveItemId());
        verify(repository).saveArchiveRef(any());
        verify(repository).updateArchiveRef(any());
        verify(repository).updateOutgoing(outgoing);
        ArgumentCaptor<ArchivePushCommand> captor = ArgumentCaptor.forClass(ArchivePushCommand.class);
        verify(archiveGateway).archive(captor.capture());
        assertEquals("OA_DOC", captor.getValue().getSourceType());
        assertEquals("OUT-" + DOC_ID, captor.getValue().getSourceId());
    }

    @Test
    @DisplayName("归档：失败记录 FAILED 并抛出")
    void shouldMarkFailedWhenArchiveUnavailable() {
        DocOutgoing outgoing = outgoing(DocStatus.OUTGOING_ISSUED);
        when(repository.findOutgoing(DOC_ID)).thenReturn(outgoing);
        when(repository.findArchiveRef(DocStatus.TYPE_OUTGOING, DOC_ID)).thenReturn(null);
        when(archiveGateway.archive(any())).thenThrow(OaErrorCode.exception(OaErrorCode.DOC_ARCHIVE_UNAVAILABLE));

        BizException exception = assertThrows(BizException.class,
                () -> archiveAppService.archive(DocStatus.TYPE_OUTGOING, DOC_ID));

        assertEquals(OaErrorCode.DOC_ARCHIVE_UNAVAILABLE, exception.getCode());
        assertEquals(DocStatus.OUTGOING_ISSUED, outgoing.getStatus());
        verify(repository).updateArchiveRef(any());
    }

    @Test
    @DisplayName("收文：登记 → 批阅 → 转发 → 办结")
    void shouldCompleteIncomingLifecycle() {
        DocIncoming incoming = incoming(DocStatus.INCOMING_REGISTERED);
        when(docNoAppService.nextNo("IN_DEFAULT")).thenReturn("BW-IN-2026-001");
        when(repository.findIncoming(DOC_ID)).thenReturn(incoming);

        DocumentViews.Incoming created = incomingAppService.create(incomingCommand());
        assertEquals(DocStatus.INCOMING_REGISTERED, created.getStatus());

        DocumentCommands.IncomingReview review = new DocumentCommands.IncomingReview();
        review.setInstruction("请办公室办理");
        review.setAssigneeId(9L);
        review.setAssigneeName("李四");
        incomingAppService.review(DOC_ID, review);
        assertEquals(DocStatus.INCOMING_REVIEWED, incoming.getStatus());

        DocumentCommands.IncomingForward forward = new DocumentCommands.IncomingForward();
        forward.setAssigneeId(9L);
        forward.setAssigneeName("李四");
        incomingAppService.forward(DOC_ID, forward);
        assertEquals(DocStatus.INCOMING_FORWARDED, incoming.getStatus());

        incomingAppService.complete(DOC_ID);
        assertEquals(DocStatus.INCOMING_COMPLETED, incoming.getStatus());
        assertNotNull(incoming.getHandledAt());
        verify(repository, times(3)).updateIncoming(any());
    }

    @Test
    @DisplayName("收文：未办结不可归档")
    void shouldRejectArchiveBeforeCompleted() {
        DocIncoming incoming = incoming(DocStatus.INCOMING_FORWARDED);
        when(repository.findIncoming(DOC_ID)).thenReturn(incoming);

        BizException exception = assertThrows(BizException.class,
                () -> incomingAppService.archive(DOC_ID));

        assertEquals(OaErrorCode.DOC_STATUS_CONFLICT, exception.getCode());
        verify(docArchiveAppService, never()).archive(anyString(), anyLong());
    }

    private DocNoRule rule(String code, String periodFormat, String prefix, int serialLength,
                           String separator) {
        DocNoRule rule = new DocNoRule();
        rule.setCode(code);
        rule.setDocType(DocStatus.TYPE_OUTGOING);
        rule.setPrefix(prefix);
        rule.setPeriodFormat(periodFormat);
        rule.setSerialLength(serialLength);
        rule.setSeparator(separator);
        rule.setEnabled(1);
        return rule;
    }

    private DocumentCommands.TemplateSave templateCommand() {
        DocumentCommands.TemplateSave command = new DocumentCommands.TemplateSave();
        command.setCode("TPL");
        command.setName("红头模板");
        command.setDocType(DocStatus.TYPE_OUTGOING);
        command.setVersion(1);
        return command;
    }

    private DocumentCommands.OutgoingSave outgoingCommand() {
        DocumentCommands.OutgoingSave command = new DocumentCommands.OutgoingSave();
        command.setTitle("关于上线 OA 的通知");
        command.setDocType("NOTICE");
        DocumentCommands.AttachmentSave attachment = new DocumentCommands.AttachmentSave();
        attachment.setFileId(123L);
        attachment.setFileName("通知.pdf");
        command.setAttachments(List.of(attachment));
        return command;
    }

    private DocumentCommands.IncomingSave incomingCommand() {
        DocumentCommands.IncomingSave command = new DocumentCommands.IncomingSave();
        command.setSourceUnit("市工信局");
        command.setSourceNo("工信〔2026〕1号");
        command.setTitle("关于数字化转型的函");
        return command;
    }

    private DocOutgoing outgoing(String status) {
        DocOutgoing outgoing = new DocOutgoing();
        outgoing.setId(DOC_ID);
        outgoing.setDocNo("BW-OUT-2026-001");
        outgoing.setTitle("关于上线 OA 的通知");
        outgoing.setDrafterId(USER_ID);
        outgoing.setDrafterName(USERNAME);
        outgoing.setStatus(status);
        return outgoing;
    }

    private DocIncoming incoming(String status) {
        DocIncoming incoming = new DocIncoming();
        incoming.setId(DOC_ID);
        incoming.setIncomingNo("BW-IN-2026-001");
        incoming.setSourceUnit("市工信局");
        incoming.setTitle("关于数字化转型的函");
        incoming.setStatus(status);
        return incoming;
    }

    private DocArchiveRef archiveRef(String status, String itemId) {
        DocArchiveRef ref = new DocArchiveRef();
        ref.setId(3L);
        ref.setDocType(DocStatus.TYPE_OUTGOING);
        ref.setDocId(DOC_ID);
        ref.setSourceType(DocStatus.ARCHIVE_SOURCE_TYPE);
        ref.setSourceId("OUT-" + DOC_ID);
        ref.setArchiveItemId(itemId);
        ref.setStatus(status);
        return ref;
    }
}
