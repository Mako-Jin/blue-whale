package com.bluewhale.oa.culture.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投票问卷聚合。
 *
 * <p>状态机：{@code DRAFT → PUBLISHED → CLOSED}；草稿可编辑，
 * 发布需至少 2 个选项且起止时间合法，关闭仅从进行中发起。</p>
 *
 * @author BlueWhale
 */
@Data
public class Poll {

    /** 主键ID */
    private Long id;

    /** 投票标题 */
    private String title;

    /** 投票说明 */
    private String description;

    /** 是否多选：0 单选 / 1 多选 */
    private Integer multipleChoice;

    /** 是否匿名：0 实名 / 1 匿名展示 */
    private Integer anonymous;

    /** 最多可选数量 */
    private Integer maxChoices;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态 */
    private String status;

    /** 创建人用户ID */
    private Long creatorId;

    /** 创建人姓名快照 */
    private String creatorName;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验是否可编辑。
     */
    public void assertEditable() {
        if (PollStatus.of(status) != PollStatus.DRAFT) {
            throw OaErrorCode.exception(OaErrorCode.POLL_STATUS_CONFLICT, "仅草稿状态的投票可编辑");
        }
    }

    /**
     * 发布投票（草稿 → 进行中）。
     *
     * @param optionCount 选项数量
     */
    public void publish(int optionCount) {
        PollStatus current = PollStatus.of(status);
        if (current == PollStatus.PUBLISHED) {
            return;
        }
        if (current != PollStatus.DRAFT) {
            throw OaErrorCode.exception(OaErrorCode.POLL_STATUS_CONFLICT, "仅草稿状态的投票可发布");
        }
        if (optionCount < 2) {
            throw OaErrorCode.exception(OaErrorCode.POLL_OPTION_REQUIRED, "投票至少需要 2 个选项才能发布");
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw OaErrorCode.exception(OaErrorCode.POLL_INVALID, "投票开始时间不能晚于结束时间");
        }
        this.status = PollStatus.PUBLISHED.name();
    }

    /**
     * 关闭投票（进行中 → 已关闭）。
     */
    public void close() {
        if (PollStatus.of(status) != PollStatus.PUBLISHED) {
            throw OaErrorCode.exception(OaErrorCode.POLL_STATUS_CONFLICT, "仅进行中的投票可关闭");
        }
        this.status = PollStatus.CLOSED.name();
    }

    /**
     * 校验当前时刻是否可投票。
     *
     * @param now 当前时间
     */
    public void assertVotable(LocalDateTime now) {
        if (PollStatus.of(status) != PollStatus.PUBLISHED) {
            throw OaErrorCode.exception(OaErrorCode.POLL_STATUS_CONFLICT, "仅进行中的投票可投票");
        }
        if (startTime != null && now.isBefore(startTime)) {
            throw OaErrorCode.exception(OaErrorCode.POLL_STATUS_CONFLICT, "投票尚未开始");
        }
        if (endTime != null && now.isAfter(endTime)) {
            throw OaErrorCode.exception(OaErrorCode.POLL_STATUS_CONFLICT, "投票已结束");
        }
    }

    /**
     * 是否多选投票。
     *
     * @return 是否多选
     */
    public boolean isMultiple() {
        return multipleChoice != null && multipleChoice == 1;
    }
}
