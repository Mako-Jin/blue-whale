package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 品牌配置保存请求（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
@Data
public class BrandConfigSaveRequest {

    /** 作用域类型：PLATFORM / TENANT / COMPANY */
    @NotBlank(message = "{validation.auth.brand.scope-required}")
    @Pattern(regexp = "PLATFORM|TENANT|COMPANY", message = "{validation.auth.brand.scope-pattern}")
    private String targetType;

    /** 作用域 ID：PLATFORM 固定 0 */
    @NotNull(message = "{validation.auth.brand.scope-id-required}")
    @Min(value = 0, message = "{validation.auth.brand.scope-id-min}")
    private Long targetId;

    /** 绑定域名：空串表示默认品牌 */
    @Size(max = 128, message = "{validation.auth.brand.domain-size}")
    private String domain;

    @NotBlank(message = "{validation.auth.brand.name-required}")
    @Size(max = 128, message = "{validation.auth.brand.name-size}")
    private String brandName;

    @Size(max = 512, message = "{validation.auth.brand.logo-size}")
    private String logoUrl;

    @Size(max = 128, message = "{validation.auth.brand.login-title-size}")
    private String loginTitle;

    @Size(max = 255, message = "{validation.auth.brand.org-name-size}")
    private String institutionName;

    @Size(max = 255, message = "{validation.auth.brand.copyright-size}")
    private String copyright;

    /** 主题色：#RGB 或 #RRGGBB，可空 */
    @Pattern(regexp = "^$|^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$",
            message = "{validation.auth.brand.theme-color-pattern}")
    private String primaryColor;

    @Size(max = 512, message = "{validation.auth.brand.background-size}")
    private String backgroundUrl;

    /** 解析优先级：数字越小越优先 */
    @Min(value = 0, message = "{validation.auth.brand.priority-min}")
    private Integer priority;

    /** 状态：1 启用 / 0 停用；为空时新建默认启用、编辑保持原状态 */
    @Min(value = 0, message = "{validation.auth.common.status-range}")
    @Max(value = 1, message = "{validation.auth.common.status-range}")
    private Integer status;
}
