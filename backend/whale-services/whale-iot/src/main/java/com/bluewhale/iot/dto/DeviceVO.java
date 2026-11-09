package com.bluewhale.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备视图对象（不含 secret；secret 仅创建/重置响应一次性返回）。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceVO {

    /** 设备ID（字符串化） */
    private String id;

    /** 所属产品ID（字符串化） */
    private String productId;

    /** 所属产品编码 */
    private String productCode;

    /** 所属产品名称 */
    private String productName;

    /** 设备名称（三元组之一） */
    private String deviceName;

    /** 设备昵称 */
    private String nickname;

    /** 在线状态：0 离线 / 1 在线 */
    private Integer online;

    /** 最近在线时间（ISO-8601，可空） */
    private String lastOnlineTime;

    /** 最近遥测上报时间（ISO-8601，可空） */
    private String lastReportTime;

    /** 状态：active/disabled */
    private String status;

    /** 创建时间（ISO-8601） */
    private String createTime;

    /** 更新时间（ISO-8601） */
    private String updateTime;
}
