package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 绑定实时态视图（运行时轮询产物，SSE 与快照接口共用）。
 *
 * <p>{@code values} 的键是属性映射里的实体状态键（如 color/label），
 * 值是设备该字段的最新取值；设备离线时 {@code online=false} 且 values 为空。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BindingStateVO {

    /** 绑定ID（字符串化） */
    private String bindingId;

    /** 孪生实体ID（字符串化） */
    private String entityId;

    /** 实体编码 */
    private String entityCode;

    /** 数据源类型 */
    private String sourceType;

    /** 绑定键（iot_device 为设备ID） */
    private String bindKey;

    /** 设备在线：true / false（internal_api 为「本次拉取是否成功」） */
    private Boolean online;

    /** 实体状态键 → 最新取值 */
    private Map<String, Object> values;

    /** 取值时间（ISO-8601，可空） */
    private String ts;
}
