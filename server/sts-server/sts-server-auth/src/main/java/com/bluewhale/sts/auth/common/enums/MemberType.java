package com.bluewhale.sts.auth.common.enums;

/**
 * 授权关系中的"成员"类型。
 *
 * <p>本枚举被**两张多态表**共用，而它们对取值的支持程度**不一样**，这一点必须说清 ——
 * 早先的注释在这里写成了一句"组可以嵌套"，并因此被误读成两条链路都支持嵌套：
 * <pre>
 *   角色-成员   {@code bw_auth_rel_role_member}        用户 ✓  用户组 ✓
 *   用户组-成员 {@code bw_auth_rel_user_group_member}  用户 ✓  用户组 ✗（接口显式拒绝）
 * </pre>
 *
 * <p><b>用户组-成员这一侧为什么不接受 {@code GROUP}：</b>组的嵌套在判定链路里
 * **不生效** —— {@code AuthorizationMapper} 的两条 SQL 都把
 * {@code gm.member_type} 钉成了 {@code 'USER'}，只认"组 → 用户"这一层。
 * 也就是说"把 B 组放进 A 组、再把角色授给 A"这条路，成员列表里一切正常、
 * 上海分部的成员却拿不到任何权限。一条**看起来生效、实际不参与判定**的关系，
 * 正是本仓最不接受的东西（假开关），所以由接口直接拒绝，而不是默默接受。
 *
 * <p>补上嵌套需要 {@code WITH RECURSIVE}，三套方言的写法与版本要求不一致
 * （达梦在 Oracle 兼容模式下的支持程度未验证过）。仓促加进去的风险是把
 * "嵌套不生效"换成"某套库上整条判定失败" —— 后者严重得多，且不会在 h2 上暴露。
 *
 * <p><b>为什么多态而不是两张表：</b>参照实现 {@code SystemTerminator} 里用户与角色
 * 之间没有独立的关联表，一律走多态表 —— 这带来一个实际好处：
 * "我为什么有这个权限"的追溯只有一条路径，排查时不必把两条链路拼起来看。
 * 代价是放弃外键约束，由应用层保证成员存在性；而且 {@code member_id} 只在
 * 它自己所指向的那张表里有意义（用户与用户组是两个独立的自增序列、会撞号）——
 * 任何按 {@code member_id} 定位的路径都必须带上本列，见
 * {@code RoleMemberService.revoke} 与 {@code UserGroupService.revokeMember}。
 */
public enum MemberType {

    USER("USER", "用户"),
    GROUP("GROUP", "用户组");

    private final String wire;
    private final String label;

    MemberType(String wire, String label) {
        this.wire = wire;
        this.label = label;
    }

    public String wireName() {
        return wire;
    }

    public String getLabel() {
        return label;
    }

    public static MemberType parse(String value) {
        if (value == null) {
            return null;
        }
        for (MemberType type : values()) {
            if (type.wire.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }

}
