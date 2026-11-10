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
    @NotBlank(message = "活动名称不能为空")
    @Size(max = 128, message = "活动名称不得超过 128 字符")
    private String title;

    /** 活动类型 */
    @Size(max = 32, message = "活动类型不得超过 32 字符")
    private String activityType;

    /** 开始时间 */
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    /** 结束时间 */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /** 活动地点 */
    @Size(max = 255, message = "活动地点不得超过 255 字符")
    private String location;

    /** 名额上限，0 表示不限 */
    @Min(value = 0, message = "名额不能为负数")
    private Integer quota;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
