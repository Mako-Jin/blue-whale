package com.bluewhale.lms.training.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 培训班聚合根。
 *
 * <p>培训班是培训计划的载体：场次（{@link ClassSession}）是本聚合内实体，
 * 学员通过必修分配（{@link TrainingAssignment}）进入班级，签到（{@link Checkin}）
 * 挂在场次下。课时台账（{@link LecturerHours}）以场次为最小计量单位。</p>
 *
 * <p>状态机见 {@link TrainingClassStatus}：仅草稿可编辑内容；发布需至少 1 个场次；
 * 已发布内容冻结（已分配学员看到的场次时间不可再变）；已结课为终态。</p>
 *
 * @author BlueWhale
 */
@Data
public class TrainingClass {

    /** 主键 */
    private Long id;

    /** 培训班编码（租户内唯一） */
    private String classCode;

    /** 培训班名称 */
    private String title;

    /** 关联课程 ID（线上培训可空） */
    private Long courseId;

    /** 形式 */
    private TrainingMode mode;

    /** 主讲讲师 */
    private String lecturer;

    /** 开班日期 */
    private LocalDate startDate;

    /** 结班日期 */
    private LocalDate endDate;

    /** 培训地点 */
    private String location;

    /** 计划人数，空为不限 */
    private Integer capacity;

    /** 状态 */
    private TrainingClassStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 发布培训班：仅草稿可发布，且至少 1 个场次。
     *
     * @param sessionCount 当前场次数
     */
    public void publish(long sessionCount) {
        if (!status.canPublish()) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT, "仅草稿培训班可发布");
        }
        if (sessionCount < 1) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT, "发布前至少配置 1 个场次");
        }
        status = TrainingClassStatus.PUBLISHED;
    }

    /**
     * 结课：仅已发布可结课，结课后为终态。
     */
    public void close() {
        if (!status.canClose()) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT, "仅已发布培训班可结课");
        }
        status = TrainingClassStatus.CLOSED;
    }

    /**
     * 断言内容可编辑（头信息 / 场次）。
     */
    public void assertEditable() {
        if (!status.canEditContent()) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT,
                    "已发布培训班的内容不可变更，请先撤回为草稿或另建班级");
        }
    }

    /**
     * 断言可分配学员。
     */
    public void assertAssignable() {
        if (!status.canAssign()) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT, "仅已发布培训班可分配学员");
        }
    }

    /**
     * 断言可签到。
     */
    public void assertCheckinable() {
        if (!status.canCheckin()) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT, "培训班未发布或已结课，不可签到");
        }
    }

    /**
     * 校验并规整开班 / 结班日期。
     */
    public void validatePeriod() {
        if (startDate == null || endDate == null) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_PERIOD_INVALID, "开班与结班日期不能为空");
        }
        if (endDate.isBefore(startDate)) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_PERIOD_INVALID, "结班日期不得早于开班日期");
        }
    }

    /**
     * 规范化培训班编码：去空格 + 大写。
     *
     * @param raw 原始编码
     * @return 规范化编码
     */
    public static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_CODE_CONFLICT, "培训班编码不能为空");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
