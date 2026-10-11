package com.bluewhale.sts.auth.common.enums;

/**
 * 授权关系中的"成员"类型。
 *
 * <p>角色与用户组都既能授予**用户**、也能授予**用户组**（组可以嵌套、也可整体授权），
 * 两种关系共用一张多态表，靠本列区分 {@code member_id} 指向哪张表。
 *
 * <p><b>为什么多态而不是两张表：</b>参照实现 {@code SystemTerminator} 里用户与角色
 * 之间没有独立的关联表，一律走多态表 —— 这带来一个实际好处：
 * "我为什么有这个权限"的追溯只有一条路径，排查时不必把两条链路拼起来看。
 * 代价是放弃外键约束，由应用层保证成员存在性。
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
