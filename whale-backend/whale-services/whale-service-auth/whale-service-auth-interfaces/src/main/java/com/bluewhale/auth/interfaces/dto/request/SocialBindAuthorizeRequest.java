package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 三方绑定授权起始请求（权限中心扩展 M2，须已登录）。
 *
 * @author BlueWhale
 */
@Data
public class SocialBindAuthorizeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道编码：dingtalk / feishu / wecom */
    @NotBlank(message = "{validation.auth.social.channel-required}")
    @Pattern(regexp = "dingtalk|feishu|wecom", message = "{validation.auth.social.channel-pattern}")
    private String provider;

    /** 绑定完成后的前端回跳相对路径（以 / 开头，防开放重定向） */
    @NotBlank(message = "{validation.auth.social.redirect-required}")
    @Pattern(regexp = "/(?!/)[^:]*", message = "{validation.auth.social.redirect-pattern}")
    private String redirect;
}
