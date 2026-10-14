package com.bluewhale.sts.auth.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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

    @Schema(description = "锁定原因，null 表示未锁定")
    private String lockReason;

    @Schema(description = "累计锁定次数：每触发一次锁定加一，登录成功归零。退避时长由它决定")
    private Integer lockLevel;

    /**
     * 是否还在等本人改口令：0 否 1 是。
     *
     * <p>它属于 ADR-0020 那条判据里"运维在界面上需要据此做决定"的一类 ——
     * 管理员重置了某人的口令之后，"他改了没有"是他最关心的下一件事，
     * 而这一列此前只在库里、接口上读不到（本人侧由 {@code MeVO} 承担）。
     */
    @Schema(description = "是否还在等本人改口令：0 否 1 是（管理员重置后置 1，本人改密后归 0）")
    private Integer mustChangePassword;

    private Long lastLoginAt;

    @Schema(description = "是否启用多因子：0 否 1 是")
    private Integer mfaEnabled;

    private String avatarUrl;

    /**
     * **仅"新建用户"这一个响应里非空**：该账号按租户的「默认角色」自动获得的角色名，
     * 按角色主键升序；该租户没有启用中的默认角色时是**空数组**。
     * 列表 / 详情 / 更新这三条路返回 {@code null}。
     *
     * <p>为什么让一个出参"只在一处有值"：因为它是**那次动作的结果**，不是用户的属性。
     * "它出生时带着什么角色"这个问题只在建号那一刻有意义 ——
     * 此后角色可以被改无数次，属性视角下只剩"现在有哪些"，
     * 而那个问题已经有 {@code GET /api/auth/users/{id}/roles} 回答（还带"经组获得"的来路）。
     *
     * <p>它也不是"契约里的空位"：生产者（{@code UserService#create}）与消费者
     * （管理台建号后的提示）两端都在。没有它，界面上那句"该账号默认没有任何菜单码"
     * 会在配了默认角色的租户里变成一句**假话** —— 而这正是上一轮刚补过的那句文案。
     */
    @Schema(description = "仅新建用户时非空：按租户默认角色自动授予的角色名；"
            + "空数组表示该租户未配置（或默认角色均已停用）。其余接口返回 null")
    private List<String> defaultRoleNames;

    private Long createTime;

    private Long updateTime;

}
