package com.bluewhale.party.union.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工会活动新建 / 编辑命令。
 *
 * <p>状态与编号不在命令里：发布、结束、取消各有独立动作，
 * 放进保存命令会让前端可以绕过「发布后冻结」。</p>
 *
 * @author BlueWhale
 */
@Data
public class ActivitySaveCommand {

    /** 活动名称 */
    @NotBlank(message = "{validation.party.union-activity.title-required}")
    @Size(max = 128, message = "{validation.party.union-activity.title-size}")
    private String title;

    /** 活动类型 */
    @Size(max = 32, message = "{validation.party.activity.activity-type-size}")
    private String activityType;

    /** 开始时间 */
    @NotNull(message = "{validation.party.activity.start-time-required}")
    private LocalDateTime startTime;

    /** 结束时间 */
    @NotNull(message = "{validation.party.activity.end-time-required}")
    private LocalDateTime endTime;

    /** 活动地点 */
    @Size(max = 255, message = "{validation.party.union-activity.location-size}")
    private String location;

    /** 名额上限，0 表示不限 */
    @Min(value = 0, message = "{validation.party.activity.quota-min}")
    private Integer quota;

    /** 备注 */
    @Size(max = 255, message = "{validation.party.activity.remark-size}")
    private String remark;
}
