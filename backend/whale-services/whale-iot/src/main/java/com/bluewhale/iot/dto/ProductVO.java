package com.bluewhale.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 产品视图对象（不回显任何密钥类字段）。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVO {

    /** 产品ID（字符串化） */
    private String id;

    /** 产品编码 */
    private String code;

    /** 产品名称 */
    private String name;

    /** 接入协议（首期 mqtt） */
    private String protocol;

    /** 产品描述 */
    private String description;

    /** 状态：draft/published/disabled */
    private String status;

    /** 已注册设备数（分页/详情附带） */
    private Long deviceCount;

    /** 创建人用户ID（字符串化，可空） */
    private String createdBy;

    /** 创建时间（ISO-8601） */
    private String createTime;

    /** 更新时间（ISO-8601） */
    private String updateTime;
}
