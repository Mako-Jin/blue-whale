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
    @Pattern(regexp = "^(warn|critical)?$", message = "最低级别只能是 warn 或 critical")
    private String minLevel;

    /** 场景级接收人（null 或空 = 回退场景创建人；上限 20 人） */
    @Size(max = 20, message = "接收人最多 20 个")
    private List<Long> userIds;
}
