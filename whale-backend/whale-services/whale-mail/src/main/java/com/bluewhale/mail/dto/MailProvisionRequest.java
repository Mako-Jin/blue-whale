package com.bluewhale.mail.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 平台代开邮箱请求（内网调用，来自 {@code whale-auth} 建用户后置动作）。
 *
 * <p>只带开通必需的最小信息：账号归属（租户 + 用户）、用户名（用来生成地址）、显示名。
 * 不接收口令——口令由服务端随机生成，请求方能指定口令就等于口令在链路上多走一跳。</p>
 *
 * @author BlueWhale
 */
@Data
public class MailProvisionRequest {

    /** 租户 ID */
    @NotNull(message = "{validation.mail.provision.tenant-required}")
    private Long tenantId;

    /** 平台用户 ID（whale-auth sys_user.id） */
    @NotNull(message = "{validation.mail.provision.user-required}")
    private Long userId;

    /** 用户名（生成邮箱地址用；不含可用字符时回退为 u{userId}） */
    @NotBlank(message = "{validation.mail.provision.username-required}")
    @Size(max = 64, message = "{validation.mail.provision.username-size}")
    private String username;

    /** 显示名（发件人名称） */
    @Size(max = 96, message = "{validation.mail.provision.display-name-size}")
    private String displayName;

    /**
     * 显式指定邮箱地址（可选）。
     *
     * <p>用于用户名是中文或含特殊字符、需要指定拼音地址的场景；
     * 留空时按 {@code username + 默认域名} 生成。</p>
     */
    @Size(max = 190, message = "{validation.mail.provision.email-size}")
    private String email;
}
