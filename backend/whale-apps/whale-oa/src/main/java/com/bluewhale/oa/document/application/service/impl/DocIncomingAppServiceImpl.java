package com.bluewhale.oa.document.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.service.DocArchiveAppService;
import com.bluewhale.oa.document.application.service.DocIncomingAppService;
import com.bluewhale.oa.document.application.service.DocNoAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocAttachment;
import com.bluewhale.oa.document.domain.model.DocIncoming;
import com.bluewhale.oa.document.domain.model.DocQuery;
import com.bluewhale.oa.document.domain.model.DocStatus;
import com.bluewhale.oa.document.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收文应用服务实现。
 *
 * <p>登记 → 批阅 → 转发 → 办结 → 归档；仅办结后允许归档。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocIncomingAppServiceImpl implements DocIncomingAppService {

    /** 默认收文文号规则 */
    private static final String DEFAULT_RULE = "IN_DEFAULT";

    private final DocumentRepository repository;

    private final DocNoAppService docNoAppService;

    private final DocArchiveAppService docArchiveAppService;

    @Override
    public PageResult<DocumentViews.Incoming> page(DocQuery query) {
        PageResult<DocIncoming> source = repository.pageIncoming(query);
        List<DocumentViews.Incoming> records = source.getRecords().stream()
                .map(this::toView)
                .toList();
        return PageResult.of(records, source.getTotal(), source.getCurrent(), source.getSize());
    }

    @Override
    public DocumentViews.Incoming detail(Long id) {
        return toDetail(requireIncoming(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Incoming create(DocumentCommands.IncomingSave command) {
        DocIncoming incoming = new DocIncoming();
        apply(incoming, command);
        incoming.setIncomingNo(docNoAppService.nextNo(DEFAULT_RULE));
        incoming.setStatus(DocStatus.INCOMING_REGISTERED);
        repository.saveIncoming(incoming);
        replaceAttachments(incoming.getId(), command.getAttachments());
        log.info("收文已登记: id={}, incomingNo={}", incoming.getId(), incoming.getIncomingNo());
        return toDetail(incoming);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Incoming update(Long id, DocumentCommands.IncomingSave command) {
        DocIncoming incoming = requireIncoming(id);
        if (!DocStatus.INCOMING_REGISTERED.equals(incoming.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
        apply(incoming, command);
        repository.updateIncoming(incoming);
        replaceAttachments(id, command.getAttachments());
        return toDetail(incoming);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DocIncoming incoming = requireIncoming(id);
        if (!DocStatus.INCOMING_REGISTERED.equals(incoming.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
        repository.deleteAttachments(DocStatus.TYPE_INCOMING, id);
        repository.deleteIncoming(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Incoming review(Long id, DocumentCommands.IncomingReview command) {
        DocIncoming incoming = requireIncoming(id);
        if (!DocStatus.INCOMING_REGISTERED.equals(incoming.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
        incoming.setInstruction(command.getInstruction());
        if (command.getAssigneeId() != null) {
            incoming.setAssigneeId(command.getAssigneeId());
            incoming.setAssigneeName(command.getAssigneeName());
        }
        incoming.setStatus(DocStatus.INCOMING_REVIEWED);
        repository.updateIncoming(incoming);
        return toDetail(incoming);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Incoming forward(Long id, DocumentCommands.IncomingForward command) {
        DocIncoming incoming = requireIncoming(id);
        if (!DocStatus.INCOMING_REVIEWED.equals(incoming.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
        if (command.getAssigneeId() != null) {
            incoming.setAssigneeId(command.getAssigneeId());
            incoming.setAssigneeName(command.getAssigneeName());
        }
        if (command.getRemark() != null) {
            incoming.setRemark(command.getRemark());
        }
        incoming.setStatus(DocStatus.INCOMING_FORWARDED);
        repository.updateIncoming(incoming);
        return toDetail(incoming);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Incoming complete(Long id) {
        DocIncoming incoming = requireIncoming(id);
        if (!DocStatus.INCOMING_FORWARDED.equals(incoming.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
        incoming.setHandledAt(LocalDateTime.now());
        incoming.setStatus(DocStatus.INCOMING_COMPLETED);
        repository.updateIncoming(incoming);
        return toDetail(incoming);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Incoming archive(Long id) {
        DocIncoming incoming = requireIncoming(id);
        if (!DocStatus.INCOMING_COMPLETED.equals(incoming.getStatus())
                && !DocStatus.INCOMING_ARCHIVED.equals(incoming.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
        docArchiveAppService.archive(DocStatus.TYPE_INCOMING, id);
        return detail(id);
    }

    private DocIncoming requireIncoming(Long id) {
        DocIncoming incoming = repository.findIncoming(id);
        if (incoming == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NOT_FOUND);
        }
        return incoming;
    }

    private void apply(DocIncoming incoming, DocumentCommands.IncomingSave command) {
        incoming.setSourceUnit(command.getSourceUnit().trim());
        incoming.setSourceNo(command.getSourceNo());
        incoming.setTitle(command.getTitle().trim());
        incoming.setSecurityLevel(command.getSecurityLevel() == null ? 2 : command.getSecurityLevel());
        incoming.setUrgency(command.getUrgency() == null ? 2 : command.getUrgency());
        incoming.setReceivedDate(command.getReceivedDate() == null ? LocalDate.now() : command.getReceivedDate());
        incoming.setDueDate(command.getDueDate());
        incoming.setRemark(command.getRemark());
    }

    private void replaceAttachments(Long docId, List<DocumentCommands.AttachmentSave> attachments) {
        repository.deleteAttachments(DocStatus.TYPE_INCOMING, docId);
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        int sortNo = 0;
        for (DocumentCommands.AttachmentSave item : attachments) {
            DocAttachment attachment = new DocAttachment();
            attachment.setDocType(DocStatus.TYPE_INCOMING);
            attachment.setDocId(docId);
            attachment.setFileId(item.getFileId());
            attachment.setFileName(item.getFileName());
            attachment.setFileSize(item.getFileSize() == null ? 0L : item.getFileSize());
            attachment.setContentType(item.getContentType());
            attachment.setSortNo(sortNo++);
            repository.saveAttachment(attachment);
        }
    }

    private DocumentViews.Incoming toView(DocIncoming incoming) {
        DocumentViews.Incoming view = new DocumentViews.Incoming();
        BeanUtils.copyProperties(incoming, view);
        return view;
    }

    private DocumentViews.Incoming toDetail(DocIncoming incoming) {
        DocumentViews.Incoming view = toView(incoming);
        view.setAttachments(repository.listAttachments(DocStatus.TYPE_INCOMING, incoming.getId()).stream()
                .map(this::toAttachmentView)
                .toList());
        view.setArchiveRef(docArchiveAppService.find(DocStatus.TYPE_INCOMING, incoming.getId()));
        return view;
    }

    private DocumentViews.Attachment toAttachmentView(DocAttachment attachment) {
        DocumentViews.Attachment view = new DocumentViews.Attachment();
        BeanUtils.copyProperties(attachment, view);
        return view;
    }
}
