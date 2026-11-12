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
    @NotBlank(message = "品牌作用域不能为空")
    @Pattern(regexp = "PLATFORM|TENANT|COMPANY", message = "品牌作用域仅支持 PLATFORM / TENANT / COMPANY")
    private String targetType;

    /** 作用域 ID：PLATFORM 固定 0 */
    @NotNull(message = "品牌作用域 ID 不能为空")
    @Min(value = 0, message = "品牌作用域 ID 不得为负数")
    private Long targetId;

    /** 绑定域名：空串表示默认品牌 */
    @Size(max = 128, message = "绑定域名长度不能超过 128")
    private String domain;

    @NotBlank(message = "品牌名称不能为空")
    @Size(max = 128, message = "品牌名称长度不能超过 128")
    private String brandName;

    @Size(max = 512, message = "Logo 地址长度不能超过 512")
    private String logoUrl;

    @Size(max = 128, message = "登录页标题长度不能超过 128")
    private String loginTitle;

    @Size(max = 255, message = "机构全称长度不能超过 255")
    private String institutionName;

    @Size(max = 255, message = "版权信息长度不能超过 255")
    private String copyright;

    /** 主题色：#RGB 或 #RRGGBB，可空 */
    @Pattern(regexp = "^$|^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$",
            message = "主题色必须为 #RGB 或 #RRGGBB 格式")
    private String primaryColor;

    @Size(max = 512, message = "背景图地址长度不能超过 512")
    private String backgroundUrl;

    /** 解析优先级：数字越小越优先 */
    @Min(value = 0, message = "解析优先级不得为负数")
    private Integer priority;

    /** 状态：1 启用 / 0 停用；为空时新建默认启用、编辑保持原状态 */
    @Min(value = 0, message = "状态仅支持 1（启用）/ 0（停用）")
    @Max(value = 1, message = "状态仅支持 1（启用）/ 0（停用）")
    private Integer status;
}
