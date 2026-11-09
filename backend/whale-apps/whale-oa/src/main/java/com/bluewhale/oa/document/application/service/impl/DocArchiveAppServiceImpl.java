package com.bluewhale.oa.document.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.document.application.service.DocArchiveAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.gateway.ArchiveGateway;
import com.bluewhale.oa.document.domain.gateway.ArchivePushCommand;
import com.bluewhale.oa.document.domain.model.DocArchiveRef;
import com.bluewhale.oa.document.domain.model.DocAttachment;
import com.bluewhale.oa.document.domain.model.DocIncoming;
import com.bluewhale.oa.document.domain.model.DocOutgoing;
import com.bluewhale.oa.document.domain.model.DocQuery;
import com.bluewhale.oa.document.domain.model.DocStatus;
import com.bluewhale.oa.document.domain.repository.DocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公文归档应用服务实现。
 *
 * <p>归档按 {@code (docType, docId)} 幂等：已归档直接返回既有档案条目；
 * 推送失败记录 {@code FAILED} 并保留单据原状态，可重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocArchiveAppServiceImpl implements DocArchiveAppService {

    /** 失败原因最大长度 */
    private static final int ERROR_MAX_LENGTH = 1000;

    private final DocumentRepository repository;

    private final ArchiveGateway archiveGateway;

    private final ObjectMapper objectMapper;

    @Override
    public PageResult<DocumentViews.ArchiveRef> page(DocQuery query) {
        PageResult<DocArchiveRef> source = repository.pageArchiveRefs(query);
        List<DocumentViews.ArchiveRef> records = source.getRecords().stream()
                .map(this::toView)
                .toList();
        return PageResult.of(records, source.getTotal(), source.getCurrent(), source.getSize());
    }

    @Override
    public DocumentViews.ArchiveRef find(String docType, Long docId) {
        DocArchiveRef ref = repository.findArchiveRef(docType, docId);
        return ref == null ? null : toView(ref);
    }

    @Override
    @Transactional(noRollbackFor = BizException.class)
    public DocumentViews.ArchiveRef retry(Long id) {
        DocArchiveRef ref = repository.findArchiveRef(id);
        if (ref == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NOT_FOUND);
        }
        archive(ref.getDocType(), ref.getDocId());
        DocArchiveRef latest = repository.findArchiveRef(ref.getDocType(), ref.getDocId());
        return latest == null ? toView(ref) : toView(latest);
    }

    @Override
    @Transactional(noRollbackFor = BizException.class)
    public String archive(String docType, Long docId) {
        DocOutgoing outgoing = null;
        DocIncoming incoming = null;
        if (DocStatus.TYPE_OUTGOING.equals(docType)) {
            outgoing = requireOutgoing(docId);
            assertArchivable(outgoing.getStatus());
        } else if (DocStatus.TYPE_INCOMING.equals(docType)) {
            incoming = requireIncoming(docId);
            assertArchivable(incoming.getStatus());
        } else {
            throw OaErrorCode.exception(OaErrorCode.DOC_INVALID);
        }

        DocArchiveRef ref = repository.findArchiveRef(docType, docId);
        if (ref != null && DocStatus.ARCHIVE_ARCHIVED.equals(ref.getStatus())
                && ref.getArchiveItemId() != null) {
            return ref.getArchiveItemId();
        }
        if (ref == null) {
            ref = new DocArchiveRef();
            ref.setDocType(docType);
            ref.setDocId(docId);
            ref.setSourceType(DocStatus.ARCHIVE_SOURCE_TYPE);
            ref.setSourceId(sourceId(docType, docId));
            ref.setStatus(DocStatus.ARCHIVE_PENDING);
            repository.saveArchiveRef(ref);
        } else {
            ref.setStatus(DocStatus.ARCHIVE_PENDING);
            ref.setErrorMessage(null);
            repository.updateArchiveRef(ref);
        }

        ArchivePushCommand command = outgoing != null ? buildOutgoing(outgoing) : buildIncoming(incoming);
        try {
            String itemId = archiveGateway.archive(command);
            ref.setArchiveItemId(itemId);
            ref.setStatus(DocStatus.ARCHIVE_ARCHIVED);
            ref.setErrorMessage(null);
            ref.setPushedAt(LocalDateTime.now());
            repository.updateArchiveRef(ref);
            if (outgoing != null) {
                outgoing.setArchiveItemId(itemId);
                outgoing.setStatus(DocStatus.OUTGOING_ARCHIVED);
                repository.updateOutgoing(outgoing);
            } else {
                incoming.setArchiveItemId(itemId);
                incoming.setStatus(DocStatus.INCOMING_ARCHIVED);
                repository.updateIncoming(incoming);
            }
            log.info("公文已归档: docType={}, docId={}, itemId={}", docType, docId, itemId);
            return itemId;
        } catch (BizException ex) {
            ref.setStatus(DocStatus.ARCHIVE_FAILED);
            ref.setErrorMessage(truncate(ex.getMessage()));
            repository.updateArchiveRef(ref);
            throw ex;
        }
    }

    /**
     * 校验单据状态是否允许归档：已归档直接放行，仅签发后的发文或办结后的收文可归档。
     *
     * @param status 单据当前状态
     */
    private void assertArchivable(String status) {
        if (DocStatus.OUTGOING_ARCHIVED.equals(status) || DocStatus.INCOMING_ARCHIVED.equals(status)) {
            return;
        }
        if (!DocStatus.OUTGOING_ISSUED.equals(status) && !DocStatus.INCOMING_COMPLETED.equals(status)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_STATUS_CONFLICT);
        }
    }

    /**
     * 按主键查询发文，不存在时抛出业务异常。
     *
     * @param id 发文主键
     * @return 发文领域对象
     */
    private DocOutgoing requireOutgoing(Long id) {
        DocOutgoing outgoing = repository.findOutgoing(id);
        if (outgoing == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NOT_FOUND);
        }
        return outgoing;
    }

    /**
     * 按主键查询收文，不存在时抛出业务异常。
     *
     * @param id 收文主键
     * @return 收文领域对象
     */
    private DocIncoming requireIncoming(Long id) {
        DocIncoming incoming = repository.findIncoming(id);
        if (incoming == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NOT_FOUND);
        }
        return incoming;
    }

    /**
     * 由发文组装归档推送命令，含快照、关键词与附件。
     *
     * @param outgoing 发文领域对象
     * @return 归档推送命令
     */
    private ArchivePushCommand buildOutgoing(DocOutgoing outgoing) {
        ArchivePushCommand command = baseCommand(DocStatus.TYPE_OUTGOING, outgoing.getId(),
                outgoing.getDocNo(), outgoing.getTitle(), outgoing.getSecurityLevel(),
                outgoing.getDrafterId() == null ? null : String.valueOf(outgoing.getDrafterId()),
                "发文 " + outgoing.getDocNo() + "《" + outgoing.getTitle() + "》");
        command.setKeywords(outgoing.getDocNo() + "," + outgoing.getTitle());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("docNo", outgoing.getDocNo());
        snapshot.put("title", outgoing.getTitle());
        snapshot.put("docType", outgoing.getDocType());
        snapshot.put("securityLevel", outgoing.getSecurityLevel());
        snapshot.put("urgency", outgoing.getUrgency());
        snapshot.put("templateName", outgoing.getTemplateName());
        snapshot.put("drafterName", outgoing.getDrafterName());
        snapshot.put("mainRecipients", outgoing.getMainRecipients());
        snapshot.put("copyRecipients", outgoing.getCopyRecipients());
        snapshot.put("content", outgoing.getContent());
        snapshot.put("signer", outgoing.getSigner());
        snapshot.put("signDate", outgoing.getSignDate());
        snapshot.put("issuedAt", outgoing.getIssuedAt());
        command.setSnapshotJson(toJson(snapshot));
        applyAttachment(command, DocStatus.TYPE_OUTGOING, outgoing.getId());
        return command;
    }

    /**
     * 由收文组装归档推送命令，含快照、关键词与附件。
     *
     * @param incoming 收文领域对象
     * @return 归档推送命令
     */
    private ArchivePushCommand buildIncoming(DocIncoming incoming) {
        ArchivePushCommand command = baseCommand(DocStatus.TYPE_INCOMING, incoming.getId(),
                incoming.getIncomingNo(), incoming.getTitle(), incoming.getSecurityLevel(),
                incoming.getAssigneeId() == null ? null : String.valueOf(incoming.getAssigneeId()),
                "收文 " + incoming.getIncomingNo() + "《" + incoming.getTitle() + "》");
        command.setKeywords(incoming.getIncomingNo() + "," + incoming.getSourceUnit());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("incomingNo", incoming.getIncomingNo());
        snapshot.put("sourceUnit", incoming.getSourceUnit());
        snapshot.put("sourceNo", incoming.getSourceNo());
        snapshot.put("title", incoming.getTitle());
        snapshot.put("securityLevel", incoming.getSecurityLevel());
        snapshot.put("urgency", incoming.getUrgency());
        snapshot.put("receivedDate", incoming.getReceivedDate());
        snapshot.put("dueDate", incoming.getDueDate());
        snapshot.put("assigneeName", incoming.getAssigneeName());
        snapshot.put("instruction", incoming.getInstruction());
        snapshot.put("handledAt", incoming.getHandledAt());
        command.setSnapshotJson(toJson(snapshot));
        applyAttachment(command, DocStatus.TYPE_INCOMING, incoming.getId());
        return command;
    }

    /**
     * 构建归档推送命令的公共字段，包括来源、标题、密级与业务主键等。
     *
     * @param docType 单据类型
     * @param docId 单据主键
     * @param businessKey 业务主键（文号）
     * @param title 标题
     * @param securityLevel 密级，为空时默认为 2
     * @param initiator 发起人标识
     * @param summary 归档摘要
     * @return 归档推送命令
     */
    private ArchivePushCommand baseCommand(String docType, Long docId, String businessKey, String title,
                                           Integer securityLevel, String initiator, String summary) {
        ArchivePushCommand command = new ArchivePushCommand();
        command.setSourceType(DocStatus.ARCHIVE_SOURCE_TYPE);
        command.setSourceId(sourceId(docType, docId));
        command.setTitle(title);
        command.setSummary(summary);
        command.setSecurityLevel(securityLevel == null ? 2 : securityLevel);
        command.setDefinitionCode(DocStatus.ARCHIVE_SOURCE_TYPE);
        command.setBusinessKey(businessKey);
        command.setInitiator(initiator);
        command.setFinishedAt(LocalDateTime.now());
        command.setFileName(businessKey + "-snapshot.json");
        return command;
    }

    /**
     * 将单据的首个附件作为归档原件信息填入推送命令。
     *
     * @param command 归档推送命令
     * @param docType 单据类型
     * @param docId 单据主键
     */
    private void applyAttachment(ArchivePushCommand command, String docType, Long docId) {
        List<DocAttachment> attachments = repository.listAttachments(docType, docId);
        if (attachments.isEmpty()) {
            return;
        }
        DocAttachment first = attachments.getFirst();
        command.setOriginalFileId(first.getFileId());
        command.setOriginalFileName(first.getFileName());
        command.setOriginalContentType(first.getContentType());
        command.setOriginalFileSize(first.getFileSize());
    }

    /**
     * 按单据类型与主键生成归档来源标识。
     *
     * @param docType 单据类型
     * @param docId 单据主键
     * @return 归档来源标识
     */
    private String sourceId(String docType, Long docId) {
        return (DocStatus.TYPE_OUTGOING.equals(docType) ? "OUT-" : "IN-") + docId;
    }

    /**
     * 将归档快照序列化为 JSON，失败时抛出归档不可用异常。
     *
     * @param snapshot 归档快照
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            log.warn("公文归档快照序列化失败: error={}", ex.getMessage());
            throw OaErrorCode.exception(OaErrorCode.DOC_ARCHIVE_UNAVAILABLE);
        }
    }

    /**
     * 截断超长错误信息到失败原因最大长度。
     *
     * @param message 原始错误信息，可为 null
     * @return 截断后的错误信息
     */
    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MAX_LENGTH ? message.substring(0, ERROR_MAX_LENGTH) : message;
    }

    /**
     * 将归档记录转换为视图，并补全关联单据的文号与标题。
     *
     * @param ref 归档记录
     * @return 归档记录视图
     */
    private DocumentViews.ArchiveRef toView(DocArchiveRef ref) {
        DocumentViews.ArchiveRef view = new DocumentViews.ArchiveRef();
        view.setId(ref.getId());
        view.setDocType(ref.getDocType());
        view.setDocId(ref.getDocId());
        view.setSourceType(ref.getSourceType());
        view.setSourceId(ref.getSourceId());
        view.setArchiveItemId(ref.getArchiveItemId());
        view.setStatus(ref.getStatus());
        view.setErrorMessage(ref.getErrorMessage());
        view.setPushedAt(ref.getPushedAt());
        view.setCreateTime(ref.getCreateTime());
        if (DocStatus.TYPE_OUTGOING.equals(ref.getDocType())) {
            DocOutgoing outgoing = repository.findOutgoing(ref.getDocId());
            if (outgoing != null) {
                view.setDocNo(outgoing.getDocNo());
                view.setDocTitle(outgoing.getTitle());
            }
        } else {
            DocIncoming incoming = repository.findIncoming(ref.getDocId());
            if (incoming != null) {
                view.setDocNo(incoming.getIncomingNo());
                view.setDocTitle(incoming.getTitle());
            }
        }
        return view;
    }
}
