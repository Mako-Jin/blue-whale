package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话响应（权限中心扩展 M3，refreshToken 会话账本条目）。
 *
 * @author BlueWhale
 */
@Data
public class SessionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String jti;
    private String familyId;
    private Long tenantId;
    private Long userId;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private boolean revoked;
    private LocalDateTime revokedAt;
    private String revokedReason;
    private Long deviceId;
}
