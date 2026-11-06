package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 工会会员（领域模型）。
 *
 * <p>「同一员工同时只能有一份在册会籍」不在数据库唯一键上强制：退会后可再入会，
 * 一人多段会籍是正常业务事实，唯一键会把再入会直接拒掉。该约束由应用层
 * 按 {@code employeeNo + ACTIVE} 校验。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionMember {

    /** 主键 */
    private Long id;

    /** 会员编号（租户内唯一，取号生成） */
    private String memberNo;

    /** 员工编号（引用 ERP） */
    private String employeeNo;

    /** 平台用户 ID */
    private Long userId;

    /** 会员姓名 */
    private String memberName;

    /** 所属部门名称（ERP 快照） */
    private String deptName;

    /** 入会日期 */
    private LocalDate joinDate;

    /** 会籍状态 */
    private UnionMemberStatus status;

    /** 退会日期 */
    private LocalDate withdrawDate;

    /** 会费基数 */
    private BigDecimal duesBase;

    /** 会费缴至月份（yyyy-MM） */
    private String duesPaidTo;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验并归一字段。
     *
     * <p>入会日期不允许未来日期：那是「还没入会就先记会籍」，后续会费与活动都会被算进来。</p>
     */
    public void validate() {
        employeeNo = requireText(employeeNo, "员工编号", 64);
        memberName = requireText(memberName, "会员姓名", 64);
        if (joinDate == null) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_INVALID, "入会日期不能为空");
        }
        if (joinDate.isAfter(LocalDate.now())) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_INVALID, "入会日期不能晚于今天");
        }
        deptName = limitOptional(deptName, "所属部门", 128);
        remark = limitOptional(remark, "备注", 255);
        duesPaidTo = normalizeMonth(duesPaidTo);
        if (duesBase == null) {
            duesBase = BigDecimal.ZERO;
        }
        if (duesBase.signum() < 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_INVALID, "会费基数不能为负数");
        }
        duesBase = duesBase.setScale(2, java.math.RoundingMode.HALF_UP);
        if (status == null) {
            status = UnionMemberStatus.ACTIVE;
        }
    }

    /**
     * 办理退会。
     *
     * @param date 退会日期
     */
    public void withdraw(LocalDate date) {
        if (status != UnionMemberStatus.ACTIVE) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_STATE_CONFLICT, "该会员已退会");
        }
        status = UnionMemberStatus.WITHDRAWN;
        withdrawDate = date == null ? LocalDate.now() : date;
        if (withdrawDate.isBefore(joinDate)) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_INVALID, "退会日期不能早于入会日期");
        }
    }

    /**
     * 是否为在册会员。
     *
     * @return 在册返回 true
     */
    public boolean isActive() {
        return status == UnionMemberStatus.ACTIVE;
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
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_INVALID, label + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_INVALID,
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
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_INVALID,
                    label + "不得超过 " + maxLength + " 字符");
        }
        return trimmed;
    }

    /**
     * 归一缴至月份（{@code yyyy-MM}）。
     *
     * @param value 原值
     * @return 归一后的月份文本
     */
    private String normalizeMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() != 7 || trimmed.charAt(4) != '-') {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_INVALID, "会费缴至月份格式应为 yyyy-MM");
        }
        try {
            java.time.YearMonth.parse(trimmed);
        } catch (java.time.format.DateTimeParseException exception) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_INVALID, "会费缴至月份格式应为 yyyy-MM");
        }
        return trimmed;
    }
}
