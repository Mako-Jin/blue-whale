package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 权限策略保存请求（权限中心扩展 M3）。
 *
 * <p>策略编码新建必填、租户内唯一、更新不可变；{@code conditionJson} 为附加条件
 * JSON 数组（可空表示无条件）。</p>
 *
 * @author BlueWhale
 */
@Data
public class PermissionStrategySaveRequest {

    /** 策略编码（新建必填，更新不可变） */
    @Size(max = 64, message = "策略编码长度不能超过 64")
    private String strategyCode;

    /** 策略名称 */
    @NotBlank(message = "策略名称不能为空")
    @Size(max = 128, message = "策略名称长度不能超过 128")
    private String strategyName;

    /** 策略类型（GRANT / DENY，新建必填） */
    @Pattern(regexp = "GRANT|DENY", message = "策略类型仅支持 GRANT / DENY")
    private String strategyType;

    /** 生效角色 ID 集合（必填非空） */
    @NotEmpty(message = "生效角色 ID 集合不能为空")
    private List<Long> roleIds;

    /** 生效资源 ID 集合（必填非空） */
    @NotEmpty(message = "生效资源 ID 集合不能为空")
    private List<Long> resourceIds;

    /** 附加条件 JSON（可空表示无条件） */
    @Size(max = 1024, message = "附加条件 JSON 长度不能超过 1024")
    private String conditionJson;

    /** 是否默认策略（同租户仅一条，由服务编排） */
    private Boolean isDefault;

    /** 优先级（可空默认 100，数字小者优先） */
    @Min(value = 0, message = "优先级不得为负数")
    private Integer priority;

    /** 状态：1 启用 / 0 停用（可空，新建默认启用） */
    @Min(value = 0, message = "状态仅支持 1（启用）/ 0（停用）")
    @Max(value = 1, message = "状态仅支持 1（启用）/ 0（停用）")
    private Integer status;

    /** 描述（可空） */
    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;
}
