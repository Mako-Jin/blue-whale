package com.bluewhale.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * IoT 产品实体，对应表 {@code iot_product}。
 *
 * <p>物模型定义的载体：设备接入按产品维度共享物模型与三元组规则；
 * 业务语义（MES 设备台账）仍归 MES，本表只管接入语义。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_product")
public class IotProduct extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 产品编码（租户内唯一，如 temp-sensor-v1） */
    private String code;

    /** 产品名称 */
    private String name;

    /** 接入协议：mqtt（首期仅此一种） */
    private String protocol;

    /** 产品描述 */
    private String description;

    /** 状态：draft 开发中 / published 已发布 / disabled 已停用 */
    private String status;

    /** 创建人用户ID */
    private Long createdBy;
}
