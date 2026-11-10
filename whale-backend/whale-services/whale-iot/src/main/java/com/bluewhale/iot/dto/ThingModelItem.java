package com.bluewhale.iot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 物模型条目（整包覆盖式保存的列表项）。
 *
 * @author BlueWhale
 */
@Data
public class ThingModelItem {

    /** 模型类别：property 属性 / event 事件 / service 服务 */
    @NotBlank(message = "模型类别不能为空")
    @Pattern(regexp = "^(property|event|service)$", message = "模型类别仅允许 property/event/service")
    private String kind;

    /** 标识符（产品+类别内唯一，如 temperature） */
    @NotBlank(message = "标识符不能为空")
    @Pattern(regexp = "^[a-z][a-zA-Z0-9]{0,63}$", message = "标识符须为小写字母开头的字母/数字，1-64 位")
    private String identifier;

    /** 功能名称 */
    @NotBlank(message = "功能名称不能为空")
    @Size(max = 128, message = "功能名称长度不能超过 128 字")
    private String name;

    /** 数据类型（属性必填：int/double/bool/string/enum/struct） */
    @Pattern(regexp = "^(int|double|bool|string|enum|struct)?$", message = "数据类型仅允许 int/double/bool/string/enum/struct")
    private String dataType;

    /** 规格（JSON 对象：取值范围/步长/枚举项/输入输出参数，可空） */
    private Object spec;

    /** 是否必填：0 否 / 1 是（默认 0） */
    private Integer required;
}
