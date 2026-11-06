package com.bluewhale.lms.cert.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 证书模板（发证口径的唯一定义处）。
 *
 * <p>模板决定三件事：能不能发（{@link CertTemplateStatus}）、按什么依据发
 * （{@link CertSource}）、发出来多久有效（{@link #validMonths}）。编号前缀也挂在模板上，
 * 这样同一租户下不同类别的证书可以各自成号段（如安全类 {@code SAFE-2026-000001}），
 * 而不是全部挤在一个流水里。</p>
 *
 * @author BlueWhale
 */
@Data
public class CertTemplate {

    /** 模板编码最大长度 */
    public static final int CODE_MAX_LENGTH = 64;

    /** 证书名称最大长度 */
    public static final int NAME_MAX_LENGTH = 128;

    /** 类别最大长度 */
    public static final int CATEGORY_MAX_LENGTH = 32;

    /** 编号前缀长度下限 */
    public static final int PREFIX_MIN_LENGTH = 2;

    /** 编号前缀长度上限 */
    public static final int PREFIX_MAX_LENGTH = 16;

    /** 有效期月数上限（50 年，超出基本是误填） */
    public static final int VALID_MONTHS_MAX = 600;

    /** 正文最大长度 */
    public static final int CONTENT_MAX_LENGTH = 4000;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 500;

    /** 默认编号前缀 */
    public static final String DEFAULT_PREFIX = "CERT";

    /** 主键 */
    private Long id;

    /** 模板编码（租户内唯一，大写归一） */
    private String templateCode;

    /** 证书名称 */
    private String name;

    /** 证书类别 */
    private String category;

    /** 发证依据 */
    private CertSource issueBasis;

    /** 有效期月数（0 为长期有效） */
    private Integer validMonths;

    /** 证书编号前缀 */
    private String certPrefix;

    /** 证书正文模板 */
    private String content;

    /** 状态 */
    private CertTemplateStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 归一模板编码：去空白并转大写。
     *
     * @param code 原始编码
     * @return 归一后的编码
     */
    public static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 归一编号前缀：去空白、转大写，空值回落为 {@link #DEFAULT_PREFIX}。
     *
     * @param prefix 原始前缀
     * @return 归一后的前缀
     */
    public static String normalizePrefix(String prefix) {
        String trimmed = prefix == null ? "" : prefix.trim().toUpperCase(Locale.ROOT);
        return trimmed.isEmpty() ? DEFAULT_PREFIX : trimmed;
    }

    /**
     * 校验模板基础字段。
     */
    public void validateBasics() {
        validateIdentity();
        validateValidity();
        validateTexts();
    }

    /**
     * 校验编码、名称、类别与编号前缀。
     */
    private void validateIdentity() {
        if (templateCode == null || templateCode.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_INVALID, "模板编码不能为空");
        }
        if (templateCode.length() > CODE_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_INVALID,
                    "模板编码不得超过 " + CODE_MAX_LENGTH + " 字符");
        }
        if (name == null || name.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_INVALID, "证书名称不能为空");
        }
        if (name.trim().length() > NAME_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_INVALID,
                    "证书名称不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (category != null && category.trim().length() > CATEGORY_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_INVALID,
                    "证书类别不得超过 " + CATEGORY_MAX_LENGTH + " 字符");
        }
        if (!certPrefix.matches("[A-Z0-9]{" + PREFIX_MIN_LENGTH + "," + PREFIX_MAX_LENGTH + "}")) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_INVALID,
                    "编号前缀只能是 " + PREFIX_MIN_LENGTH + "–" + PREFIX_MAX_LENGTH + " 位大写字母或数字");
        }
    }

    /**
     * 校验有效期口径。
     *
     * <p>长期有效用 {@code 0} 表达而不是 {@code null}：数据库列是 {@code NOT NULL}，
     * 少一个空态就少一类判空遗漏。</p>
     */
    private void validateValidity() {
        if (validMonths == null || validMonths < 0 || validMonths > VALID_MONTHS_MAX) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_INVALID,
                    "有效期月数必须在 0–" + VALID_MONTHS_MAX + " 之间（0 为长期有效）");
        }
    }

    /**
     * 校验正文与备注长度。
     */
    private void validateTexts() {
        if (content != null && content.length() > CONTENT_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_INVALID,
                    "证书正文不得超过 " + CONTENT_MAX_LENGTH + " 字符");
        }
        if (remark != null && remark.trim().length() > REMARK_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_INVALID,
                    "备注不得超过 " + REMARK_MAX_LENGTH + " 字符");
        }
    }

    /**
     * 确认模板可用于发证。
     */
    public void requireIssuable() {
        if (status == null || !status.canIssue()) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_STATE_CONFLICT,
                    "证书模板已停用，不可用于发证");
        }
    }

    /**
     * 是否为长期有效模板。
     *
     * @return 长期有效返回 true
     */
    public boolean isLongTerm() {
        return validMonths == null || validMonths <= 0;
    }

    /**
     * 按模板有效期计算截止时间。
     *
     * @param from 起算时间
     * @return 截止时间；长期有效返回 null
     */
    public LocalDateTime extendValidTo(LocalDateTime from) {
        return isLongTerm() ? null : from.plusMonths(validMonths);
    }
}
