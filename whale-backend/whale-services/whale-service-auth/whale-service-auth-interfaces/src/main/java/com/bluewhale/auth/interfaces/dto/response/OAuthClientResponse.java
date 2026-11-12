package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth2 客户端响应（权限中心扩展 M0）：不含任何密钥信息。
 *
 * @author BlueWhale
 */
@Data
public class OAuthClientResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String clientId;
    private String clientName;
    private List<String> redirectUris;
    private List<String> grantTypes;
    private List<String> scopes;
    private Integer accessTokenTtl;
    private Integer refreshTokenTtl;
    private Boolean autoApprove;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
