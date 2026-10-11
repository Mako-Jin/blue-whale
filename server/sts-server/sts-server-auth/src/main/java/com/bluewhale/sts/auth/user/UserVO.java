package com.bluewhale.sts.auth.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户出参。
 *
 * <p><b>刻意不含 {@code passwordHash} / {@code passwordAlgorithm} 等任何口令字段。</b>
 * 出参对象往往是"把实体拷一遍"最省事，但那样只要有人往实体加一个字段，
 * 它就会自动出现在接口响应里 —— 口令哈希泄露就是这么发生的。
 * 这里逐字段手写映射（{@code UserService#toVO}），代价是多敲几行，
 * 换来的是"实体加字段不会自动泄露"。
 */
@Getter
@Setter
public class UserVO {

    private Long id;

    private Long tenantId;

    private String username;

    private String displayName;

    private String email;

    private String phone;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;

    @Schema(description = "连续登录失败次数")
    private Integer failedAttempts;

    @Schema(description = "锁定至（毫秒），null 表示未锁定")
    private Long lockedUntil;

    private Long lastLoginAt;

    @Schema(description = "是否启用多因子：0 否 1 是")
    private Integer mfaEnabled;

    private String avatarUrl;

    private Long createTime;

    private Long updateTime;

}
