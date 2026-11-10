package com.bluewhale.scene.domain.model;

import lombok.Data;

/**
 * 用户场景视图偏好。
 *
 * <p>每个用户在每个租户下至多一条记录；无记录时按 2D 视图处理，
 * 默认场景为空表示由前端取首个可见场景。</p>
 *
 * @author BlueWhale
 */
@Data
public class UserPreference {

    /** 视图模式：2D（默认） */
    public static final String VIEW_MODE_2D = "2D";

    /** 视图模式：3D */
    public static final String VIEW_MODE_3D = "3D";

    /** 主键 */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 视图模式：2D / 3D */
    private String viewMode;

    /** 默认场景标识，空表示未指定 */
    private String defaultSceneKey;

    /**
     * 判断视图模式是否合法。
     *
     * @param viewMode 视图模式
     * @return 合法返回 {@code true}
     */
    public static boolean isValidViewMode(String viewMode) {
        return VIEW_MODE_2D.equals(viewMode) || VIEW_MODE_3D.equals(viewMode);
    }
}
