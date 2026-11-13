package com.bluewhale.auth.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 三方授权起始响应（权限中心扩展 M2）。
 *
 * @author BlueWhale
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocialAuthorizeResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道授权页完整 URL（前端直接跳转/弹窗） */
    private String authorizeUrl;
    /** 本次授权的防伪随机串（仅联调排障用） */
    private String state;
}
