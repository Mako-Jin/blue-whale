package com.bluewhale.twin.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 场景级告警通知设置保存请求（Wave 6.10 M2）。
 *
 * <p>整体覆盖语义：字段为 {@code null} 表示清除该场景级设置、回到「跟随全局」。
 * 通道开关只能收紧（{@code false} 关掉某通道），不能突破全局配置。</p>
 *
 * @author BlueWhale
 */
@Data
public class TwinAlertNotifySettingsRequest {

    /** 场景级 webhook 开关（null = 清除设置） */
    private Boolean webhookEnabled;

    /** 场景级站内信开关（null = 清除设置） */
    private Boolean inappEnabled;

    /** 场景级最低级别：warn / critical（null = 跟随全局） */
    @Pattern(regexp = "^(warn|critical)?$", message = "{validation.twin.alert.min-level-pattern}")
    private String minLevel;

    /** 场景级接收人（null 或空 = 回退场景创建人；上限 20 人） */
    @Size(max = 20, message = "{validation.twin.alert.user-ids-size}")
    private List<Long> userIds;

    /** 场景级恢复通知开关（null = 清除设置，跟随全局） */
    private Boolean recoveryEnabled;

    /** 场景级静默开始（HH:mm；null/空 = 清除，跟随全局） */
    @Pattern(regexp = "^(([01]?\\d|2[0-3]):[0-5]\\d)?$", message = "{validation.twin.alert.quiet-start-pattern}")
    private String quietStart;

    /** 场景级静默结束（HH:mm；可跨零点） */
    @Pattern(regexp = "^(([01]?\\d|2[0-3]):[0-5]\\d)?$", message = "{validation.twin.alert.quiet-end-pattern}")
    private String quietEnd;
}
