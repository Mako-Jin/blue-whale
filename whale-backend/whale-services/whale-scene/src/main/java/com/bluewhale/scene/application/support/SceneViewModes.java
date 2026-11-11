package com.bluewhale.scene.application.support;

/**
 * 场景视图模式口径（唯一校验处）。
 *
 * <p>Wave 6.25 起偏好存储交给 auth 统一配置（{@code sys_user_config} 的
 * {@code scene.view}），场景服务只保留业务语义校验，本类承接原
 * {@code domain.model.UserPreference} 中的枚举常量与校验方法。</p>
 *
 * @author BlueWhale
 */
public final class SceneViewModes {

    /** 视图模式：2D（默认） */
    public static final String VIEW_MODE_2D = "2D";

    /** 视图模式：3D */
    public static final String VIEW_MODE_3D = "3D";

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private SceneViewModes() {
    }

    /**
     * 判断视图模式是否合法。
     *
     * @param viewMode 视图模式
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(String viewMode) {
        return VIEW_MODE_2D.equals(viewMode) || VIEW_MODE_3D.equals(viewMode);
    }
}
