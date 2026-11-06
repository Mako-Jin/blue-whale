package com.bluewhale.lms.cert.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 证书。
 *
 * <p>三个不变量由本对象守住，应用层不再重复判断：</p>
 * <ol>
 *   <li><b>撤销是终态</b>：{@link CertStatus#REVOKED} 之后既不能再次撤销，也不能换发。
 *       否则「撤销」形同虚设——把撤销的证书换发一遍就洗白了；</li>
 *   <li><b>换发只发生一次</b>：{@link CertStatus#RENEWED} 的旧证书不再接受换发，
 *       新证书续接自己的时间轴，不会出现一条链上反复续期；</li>
 *   <li><b>过期不等于作废</b>：过期证书仍可撤销（事后发现违规要留痕）、仍可换发
 *       （复核后延期），只有撤销和换发两个动作会改变状态。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Data
public class Certificate {

    /** 编号中流水号的位宽（6 位可容纳单租户单年百万级发证） */
    public static final int SEQUENCE_WIDTH = 6;

    /** 首次发证的换发来源标识 */
    public static final long NO_RENEW_SOURCE = 0L;

    /** 撤销原因最大长度 */
    public static final int REASON_MAX_LENGTH = 500;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 500;

    /** 主键 */
    private Long id;

    /** 证书编号（租户内唯一） */
    private String certNo;

    /** 证书模板 ID */
    private Long templateId;

    /** 证书名称快照 */
    private String templateName;

    /** 类别快照 */
    private String category;

    /** 持证人用户 ID */
    private Long userId;

    /** 持证人用户名 */
    private String username;

    /** 来源类型 */
    private CertSource sourceType;

    /** 来源业务 ID（手工发证为 0） */
    private Long sourceId;

    /** 来源业务标题快照 */
    private String sourceTitle;

    /** 发证年份 */
    private Integer issueYear;

    /** 当年流水号 */
    private Integer seqNo;

    /** 发证时间 */
    private LocalDateTime issuedAt;

    /** 有效期起 */
    private LocalDateTime validFrom;

    /** 有效期止（null 为长期有效） */
    private LocalDateTime validTo;

    /** 状态 */
    private CertStatus status;

    /** 撤销时间 */
    private LocalDateTime revokedAt;

    /** 撤销人 */
    private Long revokedBy;

    /** 撤销原因 */
    private String revokeReason;

    /** 换发来源证书 ID（{@link #NO_RENEW_SOURCE} 为首次发证） */
    private Long renewFromId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 生成证书编号。
     *
     * @param prefix 编号前缀
     * @param year 发证年份
     * @param seqNo 当年流水号
     * @return 形如 {@code CERT-2026-000123} 的编号
     */
    public static String composeNo(String prefix, int year, int seqNo) {
        String normalized = CertTemplate.normalizePrefix(prefix);
        return String.format(Locale.ROOT, "%s-%d-%0" + SEQUENCE_WIDTH + "d", normalized, year, seqNo);
    }

    /**
     * 是否已过有效期。
     *
     * @param now 当前时间
     * @return 已过期返回 true
     */
    public boolean isExpired(LocalDateTime now) {
        return validTo != null && now != null && validTo.isBefore(now);
    }

    /**
     * 计算展示用状态：落库状态为有效且已过有效期时返回 {@link CertStatus#EXPIRED}。
     *
     * @param now 当前时间
     * @return 展示状态
     */
    public CertStatus effectiveStatus(LocalDateTime now) {
        CertStatus stored = status == null ? CertStatus.ACTIVE : status;
        if (stored == CertStatus.ACTIVE && isExpired(now)) {
            return CertStatus.EXPIRED;
        }
        return stored;
    }

    /**
     * 断言可撤销。
     */
    public void assertRevocable() {
        if (status == CertStatus.REVOKED) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_STATE_CONFLICT, "该证书已撤销");
        }
        if (status == CertStatus.RENEWED) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_STATE_CONFLICT,
                    "该证书已换发，请撤销换发后的新证书");
        }
    }

    /**
     * 断言可换发。
     */
    public void assertRenewable() {
        if (status == CertStatus.REVOKED) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_STATE_CONFLICT,
                    "已撤销的证书不可换发，请重新发证");
        }
        if (status == CertStatus.RENEWED) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_STATE_CONFLICT, "该证书已换发过");
        }
    }

    /**
     * 撤销证书。
     *
     * @param reason 撤销原因（必填，留痕）
     * @param operator 操作人用户 ID
     * @param now 撤销时间
     */
    public void revoke(String reason, Long operator, LocalDateTime now) {
        assertRevocable();
        if (reason == null || reason.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_INVALID, "撤销原因不能为空");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > REASON_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_INVALID,
                    "撤销原因不得超过 " + REASON_MAX_LENGTH + " 字符");
        }
        this.status = CertStatus.REVOKED;
        this.revokedAt = now;
        this.revokedBy = operator;
        this.revokeReason = trimmed;
    }

    /**
     * 标记为已换发（由换发流程在生成新证书后调用）。
     */
    public void markRenewed() {
        assertRenewable();
        this.status = CertStatus.RENEWED;
    }
}
