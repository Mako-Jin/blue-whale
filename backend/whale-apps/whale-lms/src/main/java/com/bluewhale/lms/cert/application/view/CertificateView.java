package com.bluewhale.lms.cert.application.view;

import com.bluewhale.lms.cert.domain.model.CertSource;
import com.bluewhale.lms.cert.domain.model.CertStatus;
import com.bluewhale.lms.cert.domain.model.Certificate;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 证书视图。
 *
 * <p>{@link #status} 是展示态（含派生的 {@code EXPIRED}），{@link #expired} 是同一事实的
 * 布尔投影，前端做徽标配色时不必再比时间。</p>
 *
 * @author BlueWhale
 */
@Data
public class CertificateView {

    /** 证书 ID */
    private Long id;

    /** 证书编号 */
    private String certNo;

    /** 模板 ID */
    private Long templateId;

    /** 证书名称 */
    private String templateName;

    /** 类别 */
    private String category;

    /** 持证人用户 ID */
    private Long userId;

    /** 持证人用户名 */
    private String username;

    /** 来源类型 */
    private String sourceType;

    /** 来源业务 ID */
    private Long sourceId;

    /** 来源标题 */
    private String sourceTitle;

    /** 发证年份 */
    private Integer issueYear;

    /** 当年流水号 */
    private Integer seqNo;

    /** 发证时间 */
    private LocalDateTime issuedAt;

    /** 有效期起 */
    private LocalDateTime validFrom;

    /** 有效期止（空为长期有效） */
    private LocalDateTime validTo;

    /** 是否长期有效 */
    private boolean longTerm;

    /** 展示状态：ACTIVE / EXPIRED / REVOKED / RENEWED */
    private String status;

    /** 是否已过期 */
    private boolean expired;

    /** 撤销时间 */
    private LocalDateTime revokedAt;

    /** 撤销人 */
    private Long revokedBy;

    /** 撤销原因 */
    private String revokeReason;

    /** 换发来源证书 ID（0 为首次发证） */
    private Long renewFromId;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param certificate 证书
     * @param now 当前时间（用于派生过期态）
     * @return 视图
     */
    public static CertificateView from(Certificate certificate, LocalDateTime now) {
        CertificateView view = new CertificateView();
        view.setId(certificate.getId());
        view.setCertNo(certificate.getCertNo());
        view.setTemplateId(certificate.getTemplateId());
        view.setTemplateName(certificate.getTemplateName());
        view.setCategory(certificate.getCategory());
        view.setUserId(certificate.getUserId());
        view.setUsername(certificate.getUsername());
        CertSource source = certificate.getSourceType() == null
                ? CertSource.MANUAL : certificate.getSourceType();
        view.setSourceType(source.name());
        view.setSourceId(certificate.getSourceId());
        view.setSourceTitle(certificate.getSourceTitle());
        view.setIssueYear(certificate.getIssueYear());
        view.setSeqNo(certificate.getSeqNo());
        view.setIssuedAt(certificate.getIssuedAt());
        view.setValidFrom(certificate.getValidFrom());
        view.setValidTo(certificate.getValidTo());
        view.setLongTerm(certificate.getValidTo() == null);
        CertStatus effective = certificate.effectiveStatus(now);
        view.setStatus(effective.name());
        view.setExpired(effective == CertStatus.EXPIRED);
        view.setRevokedAt(certificate.getRevokedAt());
        view.setRevokedBy(certificate.getRevokedBy());
        view.setRevokeReason(certificate.getRevokeReason());
        view.setRenewFromId(certificate.getRenewFromId());
        view.setRemark(certificate.getRemark());
        return view;
    }
}
