package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 绑定视图对象。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BindingVO {

    /** 绑定ID（字符串化） */
    private String id;

    /** 孪生实体ID（字符串化） */
    private String entityId;

    /** 实体编码（冗余展示） */
    private String entityCode;

    /** 实体名称（冗余展示） */
    private String entityName;

    /** 数据源类型：iot_device / internal_api */
    private String sourceType;

    /** 绑定键 */
    private String bindKey;

    /** 属性映射 JSON（原样回显） */
    private String propertyMap;

    /** 轮询间隔毫秒 */
    private Integer pollIntervalMs;

    /** 是否启用：0 / 1 */
    private Integer enabled;

    /** 创建时间（ISO-8601） */
    private String createTime;
}
