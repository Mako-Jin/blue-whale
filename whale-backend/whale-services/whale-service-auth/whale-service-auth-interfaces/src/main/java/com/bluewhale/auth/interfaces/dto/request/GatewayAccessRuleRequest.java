package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 网关访问名单（白名单 / 黑名单）新增/更新请求。
 *
 * <p>{@code scope}/{@code tenantId} 仅白名单使用（GW-08 租户维度）；黑名单忽略。</p>
 *
 * @author BlueWhale
 */
@Data
public class GatewayAccessRuleRequest {

    /** 路径（Ant 通配，必须以 / 开头） */
    @NotBlank(message = "路径不能为空")
    @Size(max = 255, message = "路径长度不能超过 255")
    @Pattern(regexp = "^/.*", message = "路径必须以 / 开头")
    private String pathPattern;

    /** HTTP 方法：空或 * 表示不限 */
    @Size(max = 16, message = "HTTP 方法长度不能超过 16")
    private String httpMethod;

    /** 作用域：PLATFORM 平台 / TENANT 租户；空值默认平台级（仅白名单使用） */
    @Pattern(regexp = "^(PLATFORM|TENANT)$", message = "作用域仅支持 PLATFORM / TENANT")
    private String scope;

    /** 租户 ID：平台级传 0 或空，租户级必须大于 0（仅白名单使用） */
    @Min(value = 0, message = "租户 ID 不允许为负数")
    private Long tenantId;

    /** 状态：1 启用/生效、0 停用/解除 */
    @Min(value = 0, message = "状态仅支持 1（启用/生效）/ 0（停用/解除）")
    @Max(value = 1, message = "状态仅支持 1（启用/生效）/ 0（停用/解除）")
    private Integer status;

    /** 排序号（小者优先） */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
