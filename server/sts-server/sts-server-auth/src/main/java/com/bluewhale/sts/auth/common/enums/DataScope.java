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
 * <p><b>「能不能精确求值」是这一维度的关键分野，{@link #precise} 把它显式说出来。</b>
 * {@link #ALL} 与 {@link #SELF} 是自足的：一个说"整个租户"，一个说"只有我"，
 * 不需要任何别的数据就能翻译成过滤条件。另三档不行 ——
 * {@code DEPT} 系列要知道"谁的部门是哪个"（组织归属表已有、组织接口未落地），
 * {@code CUSTOM} 要解析 {@code filter_json} 的表达式。在它们能被求值之前，
 * 任何查询都必须把它们**当作 SELF 处理**（保守方向），而不是当作 ALL。
 * 这个选择不做成"报错"：报错会让配了 DEPT 的角色整个用不了，
 * 而"暂时按仅本人"至少是安全的、且是可解释的。
 */
public enum DataScope {

    /** 全部数据 */
    ALL("ALL", "全部", 5, true),
    /** 本部门 */
    DEPT("DEPT", "本部门", 3, false),
    /** 本部门及下级 */
    DEPT_AND_CHILD("DEPT_AND_CHILD", "本部门及下级", 4, false),
    /** 仅本人 */
    SELF("SELF", "仅本人", 1, true),
    /** 自定义（条件写在 filter_json） */
    CUSTOM("CUSTOM", "自定义", 2, false);

    private final String wire;
    private final String label;

    /**
     * 合并用的**线性序**：一个人可能同时持有多个角色，各带各的数据范围，
     * 合成的结果取最宽的（权限是并集，范围也是 —— 授一个更宽的角色的效果
     * 不该被另一个更窄的角色抵消）。
     *
     * <p>数值本身没有含义，只有大小关系有。{@code CUSTOM} 的位置是刻意的**任意**：
     * 它与其他档位无法比较（一个自定义条件可能比"本部门"宽、也可能更窄）。
     * 把它放在组织范围与 SELF 之间，是因为它至少是一个显式配置的条件，
     * 而 SELF 是"什么都没配"时的安全默认 —— 显式配置应当赢过默认。
     */
    private final int rank;

    /** 能否不依赖别的数据就翻译成过滤条件；见类注释 */
    private final boolean precise;

    DataScope(String wire, String label, int rank, boolean precise) {
        this.wire = wire;
        this.label = label;
        this.rank = rank;
        this.precise = precise;
    }

    public String wireName() {
        return wire;
    }

    public String getLabel() {
        return label;
    }

    public int rank() {
        return rank;
    }

    public boolean precise() {
        return precise;
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
