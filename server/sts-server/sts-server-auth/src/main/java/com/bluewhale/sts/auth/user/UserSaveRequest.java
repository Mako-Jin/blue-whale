package com.bluewhale.sts.auth.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 用户新增 / 修改入参 */
@Schema(description = "用户保存入参")
public class UserSaveRequest {

    @Schema(description = "所属租户主键，不传则取当前登录用户所属租户")
    private Long tenantId;

    @NotBlank(message = "登录名不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_.-]{1,63}$",
            message = "登录名须以字母开头，仅含字母、数字、下划线、点与连字符，长度 2~64")
    private String username;

    @Size(max = 128, message = "姓名不能超过 128 字")
    private String displayName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过 128 字")
    private String email;

    @Size(max = 32, message = "手机号不能超过 32 字")
    private String phone;

    @Schema(description = "口令。**新增时必填**；修改时留空表示不改口令。"
            + "长度下限与字符类别由服务端的口令策略判定，故此处不固定 minLength")
    // 刻意只留上限：**长度下限与字符类别一律交给服务端的口令策略**（PasswordPolicy）。
    // 这里若也写 min = 8，策略调到 12 时就会出现"8 位口令通过 DTO、再被策略拒"，
    // 而且两次拒绝返回的是两种不同错误码 —— 强度规则只能有一个来源。
    @Size(max = 128, message = "口令不能超过 128 字")
    private String password;

    @Schema(description = "状态：ENABLED / DISABLED，缺省 ENABLED")
    private String status;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
