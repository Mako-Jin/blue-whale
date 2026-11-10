package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 场景级告警通知设置视图（Wave 6.10 M2）。
 *
 * <p>分两层：{@code webhookEnabled / inappEnabled / minLevel / userIds} 是场景里
 * 存储的原始配置（{@code null} 表示未设置）；{@code effective*} 是叠加全局配置后的
 * 实际生效值——场景只能<b>收紧</b>（关通道、抬高门槛），不能突破全局开关与门槛。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwinAlertNotifySettingsVO {

    /** 场景ID（字符串化） */
    private String sceneId;

    /** 场景级 webhook 开关（null = 未设置，跟随全局） */
    private Boolean webhookEnabled;

    /** 场景级站内信开关（null = 未设置，跟随全局） */
    private Boolean inappEnabled;

    /** 场景级最低级别（warn / critical；null = 未设置，跟随全局） */
    private String minLevel;

    /** 场景级接收人（字符串化用户ID；空表示回退场景创建人） */
    private List<String> userIds;

    /** 是否回退到场景创建人（userIds 为空时成立） */
    private boolean fallbackToCreator;

    /** 实际是否外发 webhook（全局开 + 已配地址 + 场景未关） */
    private boolean effectiveWebhookEnabled;

    /** 实际是否外发站内信（全局开 + 场景未关） */
    private boolean effectiveInappEnabled;

    /** 实际生效的最低级别（取全局与场景中更严者） */
    private String effectiveMinLevel;

    /** 全局最低级别（供前端展示「全局口径」） */
    private String globalMinLevel;

    /** 全局 webhook 是否可用（开关开且配了地址） */
    private boolean globalWebhookReady;

    /** 全局站内信是否开启 */
    private boolean globalInappEnabled;
}
