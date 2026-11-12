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
 * OAuth2 客户端保存请求（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
@Data
public class OAuthClientSaveRequest {

    /** 客户端标识：新建可空（由服务端生成），编辑时忽略 */
    @Pattern(regexp = "^$|^[A-Za-z0-9_-]{4,64}$",
            message = "客户端标识须为 4–64 位字母、数字、下划线或中划线")
    private String clientId;

    @NotBlank(message = "客户端名称不能为空")
    @Size(max = 128, message = "客户端名称长度不能超过 128")
    private String clientName;

    /** 回调地址白名单：authorization_code 模式必填，精确匹配 */
    @Size(max = 50, message = "回调地址数量不能超过 50")
    private List<String> redirectUris;

    /** 授权类型：仅支持 authorization_code / client_credentials / refresh_token */
    @NotEmpty(message = "至少需要一种授权类型")
    private List<String> grantTypes;

    @NotEmpty(message = "授权范围不能为空")
    private List<String> scopes;

    @Min(value = 60, message = "访问令牌有效期不得小于 60 秒")
    private Integer accessTokenTtl;

    @Min(value = 60, message = "刷新令牌有效期不得小于 60 秒")
    private Integer refreshTokenTtl;

    private Boolean autoApprove;

    /** 状态：1 启用 / 0 停用；为空时新建默认启用、编辑保持原状态 */
    @Min(value = 0, message = "状态仅支持 1（启用）/ 0（停用）")
    @Max(value = 1, message = "状态仅支持 1（启用）/ 0（停用）")
    private Integer status;
}
