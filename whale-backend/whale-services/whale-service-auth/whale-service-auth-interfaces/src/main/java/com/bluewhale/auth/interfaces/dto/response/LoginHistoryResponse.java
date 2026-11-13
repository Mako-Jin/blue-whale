package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录历史响应（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
@Data
public class LoginHistoryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String loginType;
    private String loginResult;
    private String failureReason;
    private String ip;
    private String userAgent;
    private String deviceFingerprint;
    private LocalDateTime loginTime;
}
