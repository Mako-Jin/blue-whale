package com.bluewhale.sts.auth.common.enums;

/**
 * 一个用户身上某个角色的**来源** —— 反查接口（{@code GET /api/auth/users/{id}/roles}）的
 * {@code source} 取值。
 *
 * <p>之所以要把它独立成一个枚举而不是让调用方去比对字符串：这个值会被前端当作
 * 分支条件（"直接授予"与"经用户组授予"在界面上是两种不同的行，撤销的方式也不同 ——
 * 前者解的是角色-成员关系，后者要去改组）。写错一个字（{@code Direct} / {@code DIRECT }）
 * 不会报错，只会让那一行静默落到 else 分支里、显示成另一种来源 ——
 * 而"这条角色到底是谁给的"正是排查权限问题时最不能错的一件事。
 *
 * <p>{@code wire} 与 {@link MemberType} 的取值**恰好重名**（都有 {@code GROUP}），
 * 但两者不是同一个概念、也不该互相赋值：{@code MemberType} 说的是"多态表的哪一列指向哪张表"，
 * 本枚举说的是"这条角色对人而言的来源"。这里保留重名是因为它们在 SQL 里正好由同一个
 * 判断翻译出来（{@code member_type = 'GROUP'} → {@code source = 'GROUP'}），
 * 强行改名（如 {@code VIA_GROUP}）反而会让那条 SQL 的对应关系变得难认。
 *
 * <p>注意 {@code RoleMapper.selectUserRoleSources} 里的 SQL 直接写了这两个字面量 ——
 * SQL 引用不了 Java 枚举，这是**唯一**一处无法消除的重复。改动 {@code wire} 时必须同步改那边。
 */
public enum RoleSource {

    /** 直接授予：{@code bw_auth_rel_role_member} 里 {@code member_type = 'USER'} 的一行 */
    DIRECT("DIRECT", "直接授予"),

    /** 经用户组授予：用户属于某个组，角色授给了那个组 */
    GROUP("GROUP", "经用户组授予");

    private final String wire;
    private final String label;

    RoleSource(String wire, String label) {
        this.wire = wire;
        this.label = label;
    }

    public String wireName() {
        return wire;
    }

    public String getLabel() {
        return label;
    }

    /** 大小写不敏感解析；无法识别返回 null（与 {@link Status} 同一条取舍：不兜底非法值） */
    public static RoleSource parse(String value) {
        if (value == null) {
            return null;
        }
        for (RoleSource source : values()) {
            if (source.wire.equalsIgnoreCase(value.trim())) {
                return source;
            }
        }
        return null;
    }

}
