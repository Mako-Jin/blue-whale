package com.bluewhale.oa.document.application.service;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.document.domain.model.DocAttachment;
import com.bluewhale.oa.document.domain.model.DocIncoming;
import com.bluewhale.oa.document.domain.model.DocOutgoing;
import com.bluewhale.oa.document.domain.model.DocStatus;
import com.bluewhale.oa.document.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 公文读取可见性守卫。
 *
 * <p>公文列表与详情端点由菜单权限 {@code sys:oa:document:list} 统一保护，而附件下载端点
 * 只带 {@code sys:oa:document:download}；若仅比对 {@code docType/docId}，持下载权限的用户
 * 可通过枚举附件 ID 取到未发布草稿或密级公文的附件。本守卫在取文件流前补齐可见性判定：</p>
 *
 * <ul>
 *   <li>发文 {@code DRAFT / REJECTED}：仅拟稿人与审批参与人可读；</li>
 *   <li>发文 {@code PENDING} 及之后（含已签发、已归档）：持下载权限的用户可读，
 *       保证审批人在流程侧查询不可用时仍能取附件；</li>
 *   <li>收文：登记即视为正式来文，持下载权限的用户可读；</li>
 *   <li>密级为「秘密」（{@code 3}）：仅拟稿人 / 承办人 / 审批参与人可读，与状态无关。</li>
 * </ul>
 *
 * <p>审批参与人由审批上下文统一判定（发起人、审批人快照、Workflow 任务处理人）；
 * 查询失败按「非参与人」处理（fail-closed）。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocReadAccessGuard {

    /** 发文审批业务类型 */
    private static final String BIZ_TYPE_OUTGOING = "DOC_OUTGOING";

    /** 密级：秘密 */
    private static final int SECURITY_SECRET = 3;

    /** 已进入流转的发文状态（持下载权限即可读） */
    private static final Set<String> OUTGOING_IN_PROGRESS = Set.of(
            DocStatus.OUTGOING_PENDING,
            DocStatus.OUTGOING_APPROVED,
            DocStatus.OUTGOING_SIGNED,
            DocStatus.OUTGOING_ISSUED,
            DocStatus.OUTGOING_ARCHIVED);

    private final DocumentRepository documentRepository;

    private final ApprovalInstanceAppService approvalInstanceAppService;

    /**
     * 校验附件可下载：附件确实挂在传入单据下，且当前用户对单据可见。
     *
     * @param docType      单据类型（{@code OUTGOING} / {@code INCOMING}）
     * @param docId        单据ID
     * @param attachmentId 附件ID
     * @return 命中的附件
     */
    public DocAttachment requireAttachment(String docType, Long docId, Long attachmentId) {
        String type = requireSupportedType(docType);
        DocAttachment attachment = attachmentId == null ? null : documentRepository.findAttachment(attachmentId);
        if (attachment == null || docId == null || attachment.getDocType() == null
                || !type.equalsIgnoreCase(attachment.getDocType().trim())
                || !docId.equals(attachment.getDocId())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NOT_FOUND);
        }
        requireReadable(type, docId);
        return attachment;
    }

    /**
     * 校验当前用户可读指定单据。
     *
     * @param docType 单据类型（{@code OUTGOING} / {@code INCOMING}）
     * @param docId   单据ID
     */
    public void requireReadable(String docType, Long docId) {
        String type = requireSupportedType(docType);
        if (docId == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NOT_FOUND);
        }
        if (DocStatus.TYPE_OUTGOING.equals(type)) {
            requireOutgoingReadable(docId);
            return;
        }
        requireIncomingReadable(docId);
    }

    /**
     * 发文可见性：秘密级与草稿/驳回限拟稿人与审批参与人，其余状态对持权限用户开放。
     *
     * @param docId 发文ID
     */
    private void requireOutgoingReadable(Long docId) {
        DocOutgoing outgoing = documentRepository.findOutgoing(docId);
        if (outgoing == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NOT_FOUND);
        }
        if (isOwner(currentUserId(), outgoing.getDrafterId()) || isApprovalParticipant(BIZ_TYPE_OUTGOING, docId)) {
            return;
        }
        if (isSecret(outgoing.getSecurityLevel())
                || !OUTGOING_IN_PROGRESS.contains(outgoing.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_FORBIDDEN);
        }
    }

    /**
     * 收文可见性：登记即对持权限用户可见，秘密级仅承办人与审批参与人可见。
     *
     * @param docId 收文ID
     */
    private void requireIncomingReadable(Long docId) {
        DocIncoming incoming = documentRepository.findIncoming(docId);
        if (incoming == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NOT_FOUND);
        }
        if (isOwner(currentUserId(), incoming.getAssigneeId())) {
            return;
        }
        if (isSecret(incoming.getSecurityLevel())) {
            throw OaErrorCode.exception(OaErrorCode.DOC_FORBIDDEN);
        }
    }

    /**
     * 判断当前用户是否为该发文审批的参与人（秘密级与未发布单据需要）。
     *
     * @param businessType 业务类型
     * @param docId        单据ID
     * @return 参与返回 {@code true}
     */
    private boolean isApprovalParticipant(String businessType, Long docId) {
        return approvalInstanceAppService.isParticipant(businessType, docId,
                currentUserId(), RequestContextHolder.getUsername());
    }

    /**
     * 解析并校验单据类型。
     *
     * @param docType 单据类型
     * @return 归一化后的大写类型
     */
    private static String requireSupportedType(String docType) {
        String type = docType == null ? "" : docType.trim().toUpperCase();
        if (!DocStatus.TYPE_OUTGOING.equals(type) && !DocStatus.TYPE_INCOMING.equals(type)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_INVALID);
        }
        return type;
    }

    /**
     * 判断是否密级为秘密。
     *
     * @param securityLevel 密级
     * @return 秘密级返回 {@code true}
     */
    private static boolean isSecret(Integer securityLevel) {
        return securityLevel != null && securityLevel >= SECURITY_SECRET;
    }

    /**
     * 判断是否归属人。
     *
     * @param userId  当前用户ID
     * @param ownerId 归属人ID
     * @return 同一人返回 {@code true}
     */
    private static boolean isOwner(Long userId, Long ownerId) {
        return userId != null && userId.equals(ownerId);
    }

    /**
     * 读取当前请求用户ID。
     *
     * @return 用户ID，未绑定时为 {@code null}
     */
    private static Long currentUserId() {
        return RequestContextHolder.getUserId();
    }
}
