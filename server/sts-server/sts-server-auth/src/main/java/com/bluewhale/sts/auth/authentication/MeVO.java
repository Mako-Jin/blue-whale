package com.bluewhale.sts.auth.authentication;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/** 当前登录用户信息 */
@Getter
@Setter
public class MeVO {

    private Long userId;

    private String username;

    private String displayName;

    private Long tenantId;

    private String tenantCode;

    @Schema(description = "角色编码集合")
    private Set<String> roles;

    @Schema(description = "权限（资源标识）集合")
    private Set<String> permissions;

}
