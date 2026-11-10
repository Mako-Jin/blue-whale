package com.bluewhale.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 物模型条目视图对象（spec 还原为 JSON 对象）。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThingModelVO {

    /** 模型类别：property/event/service */
    private String kind;

    /** 标识符 */
    private String identifier;

    /** 功能名称 */
    private String name;

    /** 数据类型（属性才有） */
    private String dataType;

    /** 规格 JSON 对象（可空） */
    private Object spec;

    /** 是否必填：0/1 */
    private Integer required;
}
