package com.bluewhale.scene.domain.model;

/**
 * 场景状态常量（与 DDL 的 {@code sc_scene.status} 取值一致）。
 *
 * <p>状态机：{@code DRAFT → PUBLISHED → OFFLINE}，下线后允许重新发布。
 * 只有 {@code PUBLISHED} 场景会出现在 {@code /scene/navigation}。</p>
 *
 * @author BlueWhale
 */
public final class SceneStatus {

    /** 草稿：尚未发布，仅管理视图可见 */
    public static final String DRAFT = "DRAFT";

    /** 已发布：导航可见 */
    public static final String PUBLISHED = "PUBLISHED";

    /** 已下线：导航不可见，可重新发布 */
    public static final String OFFLINE = "OFFLINE";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private SceneStatus() {
    }

    /**
     * 判断状态取值是否合法。
     *
     * @param status 状态值
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(String status) {
        return DRAFT.equals(status) || PUBLISHED.equals(status) || OFFLINE.equals(status);
    }
}
