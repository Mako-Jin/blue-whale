package com.bluewhale.iot.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * IoT 物模型实体，对应表 {@code iot_thing_model}。
 *
 * <p>属性/事件/服务三类功能定义，{@code spec_json} 存取值范围/步长/枚举项等规格；
 * 整包覆盖式保存（物理替换），无逐行审计需求。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_thing_model")
public class IotThingModel extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属产品ID（iot_product.id） */
    private Long productId;

    /** 模型类别：property 属性 / event 事件 / service 服务 */
    private String kind;

    /** 标识符（产品内唯一，如 temperature） */
    private String identifier;

    /** 功能名称 */
    private String name;

    /** 数据类型（属性：int/double/bool/string/enum/struct） */
    private String dataType;

    /** 规格 JSON（取值范围/步长/枚举项/输入输出参数） */
    private String specJson;

    /** 是否必填：0 否 / 1 是 */
    private Integer required;
}
