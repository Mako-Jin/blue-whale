package com.bluewhale.sts.auth.common.enums;

/**
 * 数据范围 —— 角色能在多大范围内看到数据（数据权限）。
 *
 * <p>参照实现 {@code SystemTerminator} 把这一维度放在了
 * {@code rel_role_resource_instance} 的 {@code filters} JSON 里，没有独立枚举。
 * 这里把它显式化为角色的一个属性：**数据范围是角色的固有语义**，
 * 而"具体过滤到哪几条数据"才是实例级的细节。
 *
 * <p>{@link #SELF} 是最常用的一档，也是安全默认值 —— 新建角色若不显式指定，
 * 应当只能看见自己的数据，而不是全量。
 *
 * <p><b>本切片只落地枚举与字段，判定逻辑（把数据范围翻译成 SQL 条件）在后续切片补齐。</b>
 * 现在写清楚语义，是为了避免将来在四个地方各写一遍对"部门及下级"的理解。
 */
public enum DataScope {

    /** 全部数据 */
    ALL("ALL", "全部"),
    /** 本部门 */
    DEPT("DEPT", "本部门"),
    /** 本部门及下级 */
    DEPT_AND_CHILD("DEPT_AND_CHILD", "本部门及下级"),
    /** 仅本人 */
    SELF("SELF", "仅本人"),
    /** 自定义（条件写在 filter_json） */
    CUSTOM("CUSTOM", "自定义");

    private final String wire;
    private final String label;

    DataScope(String wire, String label) {
        this.wire = wire;
        this.label = label;
    }

    public String wireName() {
        return wire;
    }

    public String getLabel() {
        return label;
    }

    public static DataScope parse(String value) {
        if (value == null) {
            return null;
        }
        for (DataScope scope : values()) {
            if (scope.wire.equalsIgnoreCase(value.trim())) {
                return scope;
            }
        }
        return null;
    }

}
