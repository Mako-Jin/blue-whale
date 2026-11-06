package com.bluewhale.party.activity.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 组织生活（三会一课与主题党日）。
 *
 * <p>{@link #expectedCount} 是「应到人数」，与签到的实到人数对照使用。
 * 应到人数刻意<b>不做自动计算</b>：流动党员、长期病休、上级列席都会让「支部在册人数」
 * 与「本次应到」不一致，自动算出来的数字看着精确，实际每次都要人工改。</p>
 *
 * @author BlueWhale
 */
@Data
public class PartyActivity {

    /** 主题最大长度 */
    public static final int TITLE_MAX_LENGTH = 200;

    /** 纪要最大长度 */
    public static final int SUMMARY_MAX_LENGTH = 2000;

    /** 地点最大长度 */
    public static final int LOCATION_MAX_LENGTH = 128;

    /** 实际召开日期与计划日期允许的偏移天数（超过说明计划没及时调整，属痕迹管理问题） */
    public static final int ALLOWED_SHIFT_DAYS = 90;

    /** 主键 */
    private Long id;

    /** 所属支部 ID */
    private Long branchId;

    /** 组织生活类型 */
    private ActivityType activityType;

    /** 活动主题 */
    private String title;

    /** 计划日期 */
    private LocalDate plannedDate;

    /** 实际召开日期 */
    private LocalDate heldDate;

    /** 活动地点 */
    private String location;

    /** 主持人 */
    private String hostName;

    /** 应到人数 */
    private Integer expectedCount;

    /** 状态 */
    private ActivityStatus status;

    /** 会议 / 活动纪要 */
    private String summary;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验字段边界与日期先后关系。
     */
    public void validate() {
        validateBasic();
        validateText();
        if (expectedCount == null || expectedCount < 0) {
            expectedCount = 0;
        }
        if (activityType == null) {
            activityType = ActivityType.MEMBER_ASSEMBLY;
        }
        if (status == null) {
            status = ActivityStatus.PLANNED;
        }
    }

    /**
     * 校验归属、主题与日期。
     */
    private void validateBasic() {
        if (branchId == null || branchId <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT, "组织生活必须归属一个支部");
        }
        if (title == null || title.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT, "活动主题不能为空");
        }
        if (title.trim().length() > TITLE_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT,
                    "活动主题不得超过 " + TITLE_MAX_LENGTH + " 字符");
        }
        if (plannedDate == null) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT, "计划日期不能为空");
        }
        if (heldDate != null && Math.abs(ChronoUnit.DAYS.between(plannedDate, heldDate)) > ALLOWED_SHIFT_DAYS) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT,
                    "实际召开日期与计划日期相差超过 " + ALLOWED_SHIFT_DAYS + " 天，请先调整计划日期");
        }
        title = title.trim();
    }

    /**
     * 校验长文本字段。
     */
    private void validateText() {
        if (location != null && location.trim().length() > LOCATION_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT,
                    "活动地点不得超过 " + LOCATION_MAX_LENGTH + " 字符");
        }
        if (summary != null && summary.trim().length() > SUMMARY_MAX_LENGTH) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT,
                    "纪要不得超过 " + SUMMARY_MAX_LENGTH + " 字符");
        }
    }

    /**
     * 断言计划可编辑（完成前可改计划，归档后一律只读）。
     */
    public void assertPlanEditable() {
        if (status != null && status.isTerminal()) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT, "已归档的组织生活不可修改");
        }
    }

    /**
     * 断言可签到（归档后不可再补签）。
     */
    public void assertSignable() {
        if (status != null && status.isTerminal()) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT, "已归档的组织生活不可签到");
        }
    }

    /**
     * 标记为已召开。
     *
     * @param actualDate 实际召开日期（空表示当天）
     * @param summaryText 会议纪要
     */
    public void complete(LocalDate actualDate, String summaryText) {
        assertPlanEditable();
        if (status == ActivityStatus.COMPLETED) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT, "该组织生活已标记完成");
        }
        this.heldDate = actualDate == null ? LocalDate.now() : actualDate;
        if (summaryText != null && !summaryText.isBlank()) {
            this.summary = summaryText.trim();
        }
        this.status = ActivityStatus.COMPLETED;
    }

    /**
     * 归档（终态）。
     */
    public void archive() {
        if (status == ActivityStatus.ARCHIVED) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT, "该组织生活已归档");
        }
        if (status != ActivityStatus.COMPLETED) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT,
                    "只有已完成并留有纪要的组织生活才能归档");
        }
        if (summary == null || summary.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT,
                    "归档前请先填写会议纪要");
        }
        this.status = ActivityStatus.ARCHIVED;
    }
}
