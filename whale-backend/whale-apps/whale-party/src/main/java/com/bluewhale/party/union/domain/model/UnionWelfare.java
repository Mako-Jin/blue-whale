package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 节日福利批次（领域模型）。
 *
 * <p>标准金额在发放时快照到明细：批次后续调标不改历史名单金额，
 * 否则已签收的人会被追溯改账，审计上无法解释。</p>
 *
 * <p>状态单向 {@code DRAFT → ISSUED → CLOSED}，发放后批次冻结，只剩签收与关闭两个动作。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionWelfare {

    /** 名称最大长度 */
    public static final int TITLE_MAX_LENGTH = 128;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 福利年度下限 */
    public static final int YEAR_MIN = 2000;

    /** 福利年度上限 */
    public static final int YEAR_MAX = 2100;

    /** 金额小数位 */
    public static final int AMOUNT_SCALE = 2;

    /** 主键 */
    private Long id;

    /** 批次编号（租户内唯一，取号生成） */
    private String welfareNo;

    /** 福利名称 */
    private String title;

    /** 福利年度 */
    private Integer welfareYear;

    /** 发放标准（元/人） */
    private BigDecimal standardAmount;

    /** 状态 */
    private WelfareStatus status;

    /** 发放时间 */
    private LocalDateTime issuedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验并归一字段。
     *
     * <p>标准金额允许为 0（有些福利是实物发放，金额只作账面占位），
     * 但仍统一归一到两位小数，避免明细与汇总口径不一致。</p>
     */
    public void validate() {
        title = requireText(title, "福利名称", TITLE_MAX_LENGTH);
        if (welfareYear == null || welfareYear < YEAR_MIN || welfareYear > YEAR_MAX) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_INVALID,
                    "福利年度应在 " + YEAR_MIN + "–" + YEAR_MAX + " 之间");
        }
        if (standardAmount == null) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_INVALID, "发放标准不能为空");
        }
        if (standardAmount.signum() < 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_INVALID, "发放标准不能为负数");
        }
        standardAmount = standardAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        remark = limitOptional(remark, "备注", REMARK_MAX_LENGTH);
        if (status == null) {
            status = WelfareStatus.DRAFT;
        }
    }

    /**
     * 发放（仅草稿 → 已发放）。
     *
     * <p>时间由领域层写入，调用方不能指定：发放时点是对账依据。</p>
     */
    public void issue() {
        if (status == null || status != WelfareStatus.DRAFT) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_STATE_CONFLICT,
                    "只有草稿状态的福利批次可以发放");
        }
        status = WelfareStatus.ISSUED;
        issuedAt = LocalDateTime.now();
    }

    /**
     * 关闭（仅已发放 → 已关闭，终态）。
     *
     * <p>允许存在未签收明细：关闭是「本批次不再等待签收」的账务动作，
     * 未签收数量在列表里作为对账差异呈现，不阻断关闭。</p>
     */
    public void close() {
        if (status == null || status != WelfareStatus.ISSUED) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_STATE_CONFLICT,
                    "只有已发放的福利批次可以关闭");
        }
        status = WelfareStatus.CLOSED;
        closedAt = LocalDateTime.now();
    }

    /**
     * 是否可编辑（仅草稿）。
     *
     * @return 可编辑返回 true
     */
    public boolean isEditable() {
        return status == null || status.isEditable();
    }

    /**
     * 是否已发放（可签收）。
     *
     * @return 已发放返回 true
     */
    public boolean isIssued() {
        return status == WelfareStatus.ISSUED;
    }

    /**
     * 必填文本校验。
     *
     * @param value 原值
     * @param label 字段名
     * @param maxLength 最大长度
     * @return 去空白后的文本
     */
    private String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_INVALID, label + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_INVALID,
                    label + "不得超过 " + maxLength + " 字符");
        }
        return trimmed;
    }

    /**
     * 选填文本校验。
     *
     * @param value 原值
     * @param label 字段名
     * @param maxLength 最大长度
     * @return 去空白后的文本（空值为 null）
     */
    private String limitOptional(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_INVALID,
                    label + "不得超过 " + maxLength + " 字符");
        }
        return trimmed;
    }
}
