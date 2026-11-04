package com.bluewhale.pdm.document.support;

import java.util.Set;

/**
 * 图文档 ACL 取值与校验
 *
 * @author BlueWhale
 */
public final class PdmDocAclConstants {

    /** 资源类型：目录 */
    public static final String RESOURCE_FOLDER = "FOLDER";

    /** 资源类型：文档 */
    public static final String RESOURCE_DOCUMENT = "DOCUMENT";

    /** 主体类型：全体用户 */
    public static final String SUBJECT_ALL = "ALL";

    /** 主体类型：用户 */
    public static final String SUBJECT_USER = "USER";

    /** 主体类型：角色 */
    public static final String SUBJECT_ROLE = "ROLE";

    /** 动作：查看 */
    public static final String ACTION_VIEW = "VIEW";

    /** 动作：编辑 */
    public static final String ACTION_EDIT = "EDIT";

    /** 动作：下载 */
    public static final String ACTION_DOWNLOAD = "DOWNLOAD";

    /** 动作：删除 */
    public static final String ACTION_DELETE = "DELETE";

    /** 动作：权限管理 */
    public static final String ACTION_MANAGE = "MANAGE";

    /** 效果：允许 */
    public static final String EFFECT_ALLOW = "ALLOW";

    /** 效果：拒绝 */
    public static final String EFFECT_DENY = "DENY";

    /** 全体主体固定标识 */
    public static final String ALL_SUBJECT_ID = "0";

    /** 合法资源类型 */
    private static final Set<String> RESOURCE_TYPES = Set.of(RESOURCE_FOLDER, RESOURCE_DOCUMENT);

    /** 合法主体类型 */
    private static final Set<String> SUBJECT_TYPES = Set.of(SUBJECT_ALL, SUBJECT_USER, SUBJECT_ROLE);

    /** 合法动作 */
    private static final Set<String> ACTIONS = Set.of(
            ACTION_VIEW, ACTION_EDIT, ACTION_DOWNLOAD, ACTION_DELETE, ACTION_MANAGE);

    /** 合法效果 */
    private static final Set<String> EFFECTS = Set.of(EFFECT_ALLOW, EFFECT_DENY);

    /**
     * 工具类禁止实例化
     */
    private PdmDocAclConstants() {
    }

    /**
     * 校验资源类型
     *
     * @param value 资源类型
     * @return 合法返回 {@code true}
     */
    public static boolean isValidResourceType(String value) {
        return RESOURCE_TYPES.contains(value);
    }

    /**
     * 校验主体类型
     *
     * @param value 主体类型
     * @return 合法返回 {@code true}
     */
    public static boolean isValidSubjectType(String value) {
        return SUBJECT_TYPES.contains(value);
    }

    /**
     * 校验动作
     *
     * @param value 动作
     * @return 合法返回 {@code true}
     */
    public static boolean isValidAction(String value) {
        return ACTIONS.contains(value);
    }

    /**
     * 校验效果
     *
     * @param value 效果
     * @return 合法返回 {@code true}
     */
    public static boolean isValidEffect(String value) {
        return EFFECTS.contains(value);
    }
}
