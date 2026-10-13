package com.bluewhale.sts.auth.authentication;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 自助改密入参。
 *
 * <p><b>为什么必须要旧口令：</b>这是自助改密与管理员重置的**分界**。
 * 管理员重置他人口令时，他不知道、也不该知道那个人的旧口令，所以那条路径不验旧口令 ——
 * 它靠 {@code auth:user:update} 权限码把关。而自助改密是"任何一个持有效令牌的请求"
 * 都能调到（不挂权限码），若不验旧口令，一次令牌泄露就等价于一次账号接管：
 * 攻击者拿到访问令牌 → 改掉口令 → 主人被关在门外，且**所有刷新令牌被撤销**，
 * 连"我还在线"这个状态都没了。
 *
 * <p>新口令的**长度下限与字符类别刻意不写在注解里**，由服务端的口令策略判定
 * （{@code 40003}）—— 与 {@code PasswordResetRequest} 同源，理由见 ADR-0020 决策 2：
 * 同一个规则两处判定会给出两种错误码，前端只能猜。这里只挡超长输入。
 */
@Schema(description = "自助改密入参")
public class ChangePasswordRequest {

    @NotBlank(message = "当前口令不能为空")
    @Size(max = 128, message = "口令不能超过 128 字")
    @Schema(description = "当前口令 —— 用于证明操作者是本人")
    private String oldPassword;

    @NotBlank(message = "新口令不能为空")
    // 同 PasswordResetRequest：长度下限与字符类别由服务端口令策略判定，这里只挡超长输入
    @Size(max = 128, message = "口令不能超过 128 字")
    @Schema(description = "新口令。长度下限与字符类别由服务端的口令策略判定，故此处不固定 minLength")
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

}
