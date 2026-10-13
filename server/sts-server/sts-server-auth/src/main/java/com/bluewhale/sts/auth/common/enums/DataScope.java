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
 * <p><b>「能不能被翻译成过滤条件」是这一维度的关键分野，但它是两个性质而不是一个：</b>
 * <ul>
 *   <li>{@link #selfSufficient()} —— ALL 与 SELF 是**自足**的：一个说"整个租户"、
 *       一个说"只有我"，不需要任何别的数据就能落到 SQL 上；</li>
 *   <li>{@link #needsOrgContext()} —— DEPT 与 DEPT_AND_CHILD **需要组织归属**：
 *       得先知道"这个人的主岗在哪个部门"，才能把"本部门"翻译成一组组织 id。
 *       这一档在组织接口落地之前**求不出来**（{@code DataScopeService} 里那句
 *       "需要组织归属" 曾是恒真的）；组织树与成员归属落地之后，
 *       它可以被求值了 —— 但有一个前提：**这个人真的有一个有效的主岗**。
 *       没有主岗（或主岗所在部门被停用）时仍然求不出，此时必须往**窄**兜底；</li>
 *   <li>{@link #CUSTOM} —— 既不自足、也不是组织问题：它要解析
 *       {@code bw_auth_role.filter_json} 里的表达式，**本仓仍未实现**。</li>
 * </ul>
 * 因此"精确求值"是**合并阶段**（{@link #needsOrgContext()} 加上运行时是否真有组织）
 * 才能回答的问题，不是枚举自己能回答的问题 —— 这正是 {@code UserDataScopeVO.precise}
 * 由 {@code DataScopeService.merge} 计算并附带 {@code note} 的原因。
 *
 * <p>对求不出的档位，任何查询都必须把它们**当作 SELF 处理**（保守方向），
 * 而不是当作 ALL。这个选择不做成"报错"：报错会让配了 DEPT 的角色整个用不了，
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

    /** 是否**自足** —— 不需要组织归属就能翻译成过滤条件（只有 ALL 与 SELF）；见类注释 */
    private final boolean selfSufficient;

    DataScope(String wire, String label, int rank, boolean selfSufficient) {
        this.wire = wire;
        this.label = label;
        this.rank = rank;
        this.selfSufficient = selfSufficient;
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

    /**
     * 不需要任何额外数据就能翻译成过滤条件（只有 ALL 与 SELF）。
     *
     * <p>名字从 {@code precise} 改过来，因为旧名字把两件事混成了一件：
     * "这一档自足"与"这一档最终能被精确求值"。后者还要看运行时有没有组织归属 ——
     * 一个配了 DEPT 但没有主岗的人，仍然求不出精确范围。
     */
    public boolean selfSufficient() {
        return selfSufficient;
    }

    /**
     * 需要"这个人的主岗在哪个组织"才能求值。
     *
     * <p>它是 {@code DataScopeService} 里唯一的分支判据：为真时去解析主岗组织，
     * 解析得到就精确求值、解析不到就**往窄兜底**（而不是当作 ALL）。
     */
    public boolean needsOrgContext() {
        return this == DEPT || this == DEPT_AND_CHILD;
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
