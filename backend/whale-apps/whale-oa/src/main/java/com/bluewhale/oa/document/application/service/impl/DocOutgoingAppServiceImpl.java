package com.bluewhale.oa.document.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalInstanceRegisterCommand;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.service.DocArchiveAppService;
import com.bluewhale.oa.document.application.service.DocNoAppService;
import com.bluewhale.oa.document.application.service.DocOutgoingAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocAttachment;
import com.bluewhale.oa.document.domain.model.DocOutgoing;
import com.bluewhale.oa.document.domain.model.DocQuery;
import com.bluewhale.oa.document.domain.model.DocStatus;
import com.bluewhale.oa.document.domain.model.DocTemplate;
import com.bluewhale.oa.document.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 发文应用服务实现。
 *
 * <p>拟稿即分配文号；提交时登记审批实例；签发须先审核通过，归档须先下发。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocOutgoingAppServiceImpl implements DocOutgoingAppService {

    /** 默认发文文号规则 */
    private static final String DEFAULT_RULE = "OUT_DEFAULT";

    /** 审批业务类型 */
    private static final String BUSINESS_TYPE = "DOC_OUTGOING";

    /** 合法文种 */
    private static final Set<String> DOC_CATEGORIES = Set.of(
            "NOTICE", "REQUEST", "REPLY", "LETTER", "MINUTES", "ANNOUNCEMENT");

    private final DocumentRepository repository;

    private final DocNoAppService docNoAppService;

    private final DocArchiveAppService docArchiveAppService;

    private final ApprovalInstanceAppService approvalInstanceAppService;

    @Override
    public PageResult<DocumentViews.Outgoing> page(DocQuery query) {
        PageResult<DocOutgoing> source = repository.pageOutgoing(query);
        List<DocumentViews.Outgoing> records = source.getRecords().stream()
                .map(this::toView)
                .toList();
        return PageResult.of(records, source.getTotal(), source.getCurrent(), source.getSize());
    }

    @Override
    public DocumentViews.Outgoing detail(Long id) {
        return toDetail(requireOutgoing(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Outgoing create(DocumentCommands.OutgoingSave command, Long drafterId,
                                         String drafterName) {
        DocOutgoing outgoing = new DocOutgoing();
        apply(outgoing, command);
        outgoing.setDocNo(docNoAppService.nextNo(DEFAULT_RULE));
        outgoing.setDrafterId(drafterId);
        outgoing.setDrafterName(drafterName);
        outgoing.setStatus(DocStatus.OUTGOING_DRAFT);
        repository.saveOutgoing(outgoing);
        replaceAttachments(outgoing.getId(), command.getAttachments());
        log.info("发文拟稿已创建: id={}, docNo={}", outgoing.getId(), outgoing.getDocNo());
        return toDetail(outgoing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Outgoing update(Long id, DocumentCommands.OutgoingSave command) {
        DocOutgoing outgoing = requireOutgoing(id);
        assertEditable(outgoing.getStatus());
        apply(outgoing, command);
        repository.updateOutgoing(outgoing);
        replaceAttachments(id, command.getAttachments());
        return toDetail(outgoing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DocOutgoing outgoing = requireOutgoing(id);
        assertEditable(outgoing.getStatus());
        repository.deleteAttachments(DocStatus.TYPE_OUTGOING, id);
        repository.deleteOutgoing(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Outgoing submit(Long id) {
        DocOutgoing outgoing = requireOutgoing(id);
        assertEditable(outgoing.getStatus());
        outgoing.setStatus(DocStatus.OUTGOING_PENDING);
        repository.updateOutgoing(outgoing);
        registerApproval(outgoing);
        return toDetail(outgoing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Outgoing audit(Long id, DocumentCommands.OutgoingAudit command) {
        DocOutgoing outgoing = requireOutgoing(id);
        if (!DocStatus.OUTGOING_PENDING.equals(outgoing.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
        outgoing.setStatus(Boolean.TRUE.equals(command.getApproved())
                ? DocStatus.OUTGOING_APPROVED : DocStatus.OUTGOING_REJECTED);
        repository.updateOutgoing(outgoing);
        log.info("发文审核完成: id={}, approved={}", id, command.getApproved());
        return toDetail(outgoing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Outgoing sign(Long id, DocumentCommands.OutgoingSign command) {
        DocOutgoing outgoing = requireOutgoing(id);
        if (!DocStatus.OUTGOING_APPROVED.equals(outgoing.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
        outgoing.setSigner(StringUtils.hasText(command.getSigner()) ? command.getSigner() : outgoing.getSigner());
        outgoing.setSignDate(command.getSignDate() == null ? LocalDate.now() : command.getSignDate());
        outgoing.setStatus(DocStatus.OUTGOING_SIGNED);
        repository.updateOutgoing(outgoing);
        return toDetail(outgoing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Outgoing issue(Long id) {
        DocOutgoing outgoing = requireOutgoing(id);
        if (!DocStatus.OUTGOING_SIGNED.equals(outgoing.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
        outgoing.setIssuedAt(LocalDateTime.now());
        outgoing.setStatus(DocStatus.OUTGOING_ISSUED);
        repository.updateOutgoing(outgoing);
        return toDetail(outgoing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Outgoing archive(Long id) {
        DocOutgoing outgoing = requireOutgoing(id);
        if (!DocStatus.OUTGOING_ISSUED.equals(outgoing.getStatus())
                && !DocStatus.OUTGOING_ARCHIVED.equals(outgoing.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
        docArchiveAppService.archive(DocStatus.TYPE_OUTGOING, id);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncApprovalResult(Long businessId, String result) {
        DocOutgoing outgoing = requireOutgoing(businessId);
        if (!DocStatus.OUTGOING_PENDING.equals(outgoing.getStatus())) {
            return;
        }
        outgoing.setStatus("APPROVED".equalsIgnoreCase(result)
                ? DocStatus.OUTGOING_APPROVED : DocStatus.OUTGOING_REJECTED);
        repository.updateOutgoing(outgoing);
    }

    private DocOutgoing requireOutgoing(Long id) {
        DocOutgoing outgoing = repository.findOutgoing(id);
        if (outgoing == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NOT_FOUND);
        }
        return outgoing;
    }

    private void assertEditable(String status) {
        if (!DocStatus.OUTGOING_DRAFT.equals(status) && !DocStatus.OUTGOING_REJECTED.equals(status)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
    }

    private void registerApproval(DocOutgoing outgoing) {
        ApprovalInstanceRegisterCommand command = new ApprovalInstanceRegisterCommand();
        command.setBusinessType(BUSINESS_TYPE);
        command.setBusinessId(outgoing.getId());
        command.setBusinessCode(outgoing.getDocNo());
        command.setTitle("发文审批-" + outgoing.getTitle());
        command.setInitiatorId(outgoing.getDrafterId());
        command.setInitiatorName(outgoing.getDrafterName());
        command.setRemark(outgoing.getRemark());
        ApprovalInstanceView instance = approvalInstanceAppService.register(command);
        outgoing.setApprovalInstanceId(instance.getId());
        repository.updateOutgoing(outgoing);
    }

    private void apply(DocOutgoing outgoing, DocumentCommands.OutgoingSave command) {
        outgoing.setTitle(command.getTitle().trim());
        outgoing.setDocType(normalizeCategory(command.getDocType()));
        outgoing.setSecurityLevel(command.getSecurityLevel() == null ? 2 : command.getSecurityLevel());
        outgoing.setUrgency(command.getUrgency() == null ? 2 : command.getUrgency());
        outgoing.setDeptId(command.getDeptId());
        outgoing.setMainRecipients(command.getMainRecipients());
        outgoing.setCopyRecipients(command.getCopyRecipients());
        outgoing.setContent(command.getContent());
        outgoing.setRemark(command.getRemark());
        if (command.getTemplateId() != null) {
            DocTemplate template = repository.findTemplate(command.getTemplateId());
            if (template == null) {
                throw OaErrorCode.exception(OaErrorCode.DOC_TEMPLATE_NOT_FOUND);
            }
            outgoing.setTemplateId(template.getId());
            outgoing.setTemplateName(template.getName());
        } else {
            outgoing.setTemplateId(null);
            outgoing.setTemplateName(null);
        }
    }

    private String normalizeCategory(String docType) {
        String value = StringUtils.hasText(docType)
                ? docType.trim().toUpperCase() : "NOTICE";
        return DOC_CATEGORIES.contains(value) ? value : "NOTICE";
    }

    private void replaceAttachments(Long docId, List<DocumentCommands.AttachmentSave> attachments) {
        repository.deleteAttachments(DocStatus.TYPE_OUTGOING, docId);
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        int sortNo = 0;
        for (DocumentCommands.AttachmentSave item : attachments) {
            DocAttachment attachment = new DocAttachment();
            attachment.setDocType(DocStatus.TYPE_OUTGOING);
            attachment.setDocId(docId);
            attachment.setFileId(item.getFileId());
            attachment.setFileName(item.getFileName());
            attachment.setFileSize(item.getFileSize() == null ? 0L : item.getFileSize());
            attachment.setContentType(item.getContentType());
            attachment.setSortNo(sortNo++);
            repository.saveAttachment(attachment);
        }
    }

    private DocumentViews.Outgoing toView(DocOutgoing outgoing) {
        DocumentViews.Outgoing view = new DocumentViews.Outgoing();
        BeanUtils.copyProperties(outgoing, view);
        return view;
    }

    private DocumentViews.Outgoing toDetail(DocOutgoing outgoing) {
        DocumentViews.Outgoing view = toView(outgoing);
        view.setAttachments(repository.listAttachments(DocStatus.TYPE_OUTGOING, outgoing.getId()).stream()
                .map(this::toAttachmentView)
                .toList());
        view.setArchiveRef(docArchiveAppService.find(DocStatus.TYPE_OUTGOING, outgoing.getId()));
        return view;
    }

    private DocumentViews.Attachment toAttachmentView(DocAttachment attachment) {
        DocumentViews.Attachment view = new DocumentViews.Attachment();
        BeanUtils.copyProperties(attachment, view);
        return view;
    }
}
