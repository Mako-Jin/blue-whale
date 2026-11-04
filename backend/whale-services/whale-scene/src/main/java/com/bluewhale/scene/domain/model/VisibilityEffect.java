package com.bluewhale.scene.domain.model;

/**
 * 可见性效果常量（与 DDL 的 {@code sc_visibility.effect} 取值一致）。
 *
 * <p>{@code DENY} 优先：同一主体上只要命中任意 DENY，即视为不可见。</p>
 *
 * @author BlueWhale
 */
public final class VisibilityEffect {

    /** 允许 */
    public static final String ALLOW = "ALLOW";

    /** 拒绝 */
    public static final String DENY = "DENY";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private VisibilityEffect() {
    }

    /**
     * 判断效果取值是否合法。
     *
     * @param effect 效果
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(String effect) {
        return ALLOW.equals(effect) || DENY.equals(effect);
    }
}
