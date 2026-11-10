package com.bluewhale.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * IoT 指令实体，对应表 {@code iot_command}。
 *
 * <p>{@code message_id} 为幂等键：同一 ID 重复下发返回既有指令，不重复投递。
 * 状态机 {@code pending → sent → success/failed}，{@code timeout} 由后续超时扫描置位。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_command")
public class IotCommand extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 目标设备ID（iot_device.id） */
    private Long deviceId;

    /** 服务标识符（物模型 service） */
    private String identifier;

    /** 指令参数 JSON（对齐物模型服务入参） */
    private String paramsJson;

    /** 消息ID（幂等键；下发方生成或服务端签发） */
    private String messageId;

    /** 状态：pending 待下发 / sent 已下发 / success 成功 / failed 失败 / timeout 超时 */
    private String status;

    /** 设备回执 JSON（成功输出或失败原因） */
    private String replyJson;

    /** 下发人用户ID */
    private Long issuedBy;

    /** 下发时间 */
    private LocalDateTime issuedTime;

    /** 回执时间 */
    private LocalDateTime replyTime;
}
