package com.bluewhale.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * IoT 设备实体，对应表 {@code iot_device}。
 *
 * <p>三元组 = 产品编码 + 设备名 + secret；secret 签发后仅可重置、接口不回显；
 * 影子 {@code shadow_json} 与在线态由 M2 遥测/连接 Webhook 维护。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_device")
public class IotDevice extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属产品ID（iot_product.id） */
    private Long productId;

    /** 设备名称（产品内唯一；三元组之一） */
    private String deviceName;

    /** 设备密钥（三元组之一；签发后仅可重置） */
    private String secret;

    /** 设备昵称（展示用） */
    private String nickname;

    /** 设备影子 JSON（最新上报属性快照 + 期望值） */
    private String shadowJson;

    /** 在线状态：0 离线 / 1 在线（由连接/断开 Webhook 维护） */
    private Integer online;

    /** 最近在线时间 */
    private LocalDateTime lastOnlineTime;

    /** 最近遥测上报时间 */
    private LocalDateTime lastReportTime;

    /** 状态：active 启用 / disabled 禁用 */
    private String status;
}
