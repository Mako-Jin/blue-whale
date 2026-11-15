package com.bluewhale.form.support;

import java.util.List;
import java.util.Set;

/**
 * 表单字段级角色权限判定。
 *
 * <p>规则：字段未配置角色列表（空）表示对所有角色开放；配置了角色列表时，
 * 当前请求角色与之有交集才放行；当前请求未携带角色（服务间内网调用）时不做限制。</p>
 *
 * @author BlueWhale
 */
public final class FormFieldPermission {

    /** 工具类不允许实例化 */
    private FormFieldPermission() {
    }

    /**
     * 判断字段对当前请求是否可见
     *
     * @param field 字段定义
     * @return 可见返回 {@code true}
     */
    public static boolean visible(FormField field) {
        return allowed(field.getVisibleRoles());
    }

    /**
     * 判断字段对当前请求是否可编辑
     *
     * @param field 字段定义
     * @return 可编辑返回 {@code true}
     */
    public static boolean editable(FormField field) {
        return allowed(field.getEditableRoles());
    }

    /**
     * 判断角色列表是否对当前请求放行
     *
     * @param requiredRoles 字段配置的角色列表（可空）
     * @return 放行返回 {@code true}
     */
    private static boolean allowed(List<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return true;
        }
        Set<String> current = FormRoleContext.get();
        if (current.isEmpty()) {
            return true;
        }
        for (String role : requiredRoles) {
            if (current.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
