package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工会活动（领域模型）。
 *
 * <p>名额为 0 表示不限；发布后时间、地点与名额冻结，避免已报名的人看到的活动信息
 * 在报名期间被悄悄改掉。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionActivity {

    /** 活动名称最大长度 */
    public static final int TITLE_MAX_LENGTH = 128;

    /** 活动类型最大长度 */
    public static final int TYPE_MAX_LENGTH = 32;

    /** 地点最大长度 */
    public static final int LOCATION_MAX_LENGTH = 255;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 主键 */
    private Long id;

    /** 活动编号（租户内唯一，取号生成） */
    private String activityNo;

    /** 活动名称 */
    private String title;

    /** 活动类型（文体 / 团建 / 公益等自由文本） */
    private String activityType;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 活动地点 */
    private String location;

    /** 名额上限，0 表示不限 */
    private Integer quota;

    /** 状态 */
    private UnionActivityStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验并归一字段。
     */
    public void validate() {
        title = requireText(title, "活动名称", TITLE_MAX_LENGTH);
        activityType = limitOptional(activityType, "活动类型", TYPE_MAX_LENGTH);
        if (startTime == null || endTime == null) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_INVALID, "活动开始与结束时间不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_INVALID,
                    "活动结束时间必须晚于开始时间");
        }
        location = limitOptional(location, "活动地点", LOCATION_MAX_LENGTH);
        if (quota == null) {
            quota = 0;
        }
        if (quota < 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_INVALID, "活动名额不能为负数");
        }
        remark = limitOptional(remark, "备注", REMARK_MAX_LENGTH);
        if (status == null) {
            status = UnionActivityStatus.DRAFT;
        }
    }

    /**
     * 发布（仅草稿 → 已发布）。
     */
    public void publish() {
        if (status == null || status != UnionActivityStatus.DRAFT) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT,
                    "只有草稿状态的活动可以发布");
        }
        status = UnionActivityStatus.PUBLISHED;
    }

    /**
     * 结束（仅已发布 → 已结束，终态）。
     */
    public void close() {
        if (status == null || status != UnionActivityStatus.PUBLISHED) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT,
                    "只有已发布的活动可以结束");
        }
        status = UnionActivityStatus.CLOSED;
    }

    /**
     * 取消（草稿或已发布 → 已取消，终态）。
     */
    public void cancel() {
        if (status == null || status.isTerminal()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT,
                    "活动已处于终态，不能再取消");
        }
        status = UnionActivityStatus.CANCELLED;
    }

    /**
     * 是否可编辑（仅草稿）。
     *
     * @return 可编辑返回 true
     */
    public boolean isEditable() {
        return status == null || status.isDraft();
    }

    /**
     * 是否已发布。
     *
     * @return 已发布返回 true
     */
    public boolean isPublished() {
        return status == UnionActivityStatus.PUBLISHED;
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
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_INVALID, label + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_INVALID,
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
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_INVALID,
                    label + "不得超过 " + maxLength + " 字符");
        }
        return trimmed;
    }
}
