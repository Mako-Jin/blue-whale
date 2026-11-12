package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员重置口令响应（REQ-SEC-023）：临时口令仅本次响应可见
 *
 * @author BlueWhale
 */
@Data
public class ResetPasswordResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    /** 一次性临时口令（不落库、不记审计参数，仅展示一次） */
    private String temporaryPassword;
    /** 是否已置强制首登改密 */
    private boolean mustChangePassword;
}
