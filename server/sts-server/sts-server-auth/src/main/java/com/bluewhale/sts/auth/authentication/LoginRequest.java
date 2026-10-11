package com.bluewhale.sts.auth.authentication;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参。
 *
 * <p>用"租户编码 + 登录名 + 口令"三元组定位账号：用户名的唯一性是**租户内**的，
 * 因此只给用户名无法唯一定位到人。
 *
 * <p>{@code tenantCode} 也可以换成"先选租户再登录"的两步式（参照实现的
 * {@code preLogin} 就是为此），但那是为"同一账号可能属于多个租户"的场景准备的。
 * 本切片的多租户模型是用户归属单一租户，两步式只会多一次往返，故合并成一步；
 * 若将来支持跨租户身份，再引入 {@code preLogin}。
 */
public class LoginRequest {

    @NotBlank(message = "租户编码不能为空")
    @Schema(description = "租户编码", example = "default")
    private String tenantCode;

    @NotBlank(message = "登录名不能为空")
    @Schema(description = "登录名", example = "admin")
    private String username;

    @NotBlank(message = "口令不能为空")
    @Schema(description = "口令", example = "Admin@12345")
    private String password;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
