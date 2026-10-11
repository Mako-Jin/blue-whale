package com.bluewhale.sts.auth.common;

import java.util.List;

/**
 * 权限标识常量。
 *
 * <p>权限标识（即资源标识）是**注解与种子数据、后台配置三处都要用到的同一个字符串**。
 * 散着写成字面量，一旦有一处拼错（{@code auth:user:ceate}），
 * 症状是"注解写了、后台也配了，但就是不生效"——两边都以为自己写对了。
 * 收成常量后，拼写错误在编译期就不存在。
 *
 * <p>命名格式统一为 {@code 域:资源:动作}，全小写、冒号分隔。
 * 它同时也是后台人员配置权限时看到的标识，因此要**稳定**：
 * 改一个标识等于改所有引用它的注解与后台配置。
 */
public final class AuthPermissions {

    private AuthPermissions() {
    }

    public static final String TENANT_READ = "auth:tenant:read";
    public static final String TENANT_CREATE = "auth:tenant:create";
    public static final String TENANT_UPDATE = "auth:tenant:update";
    public static final String TENANT_DELETE = "auth:tenant:delete";

    public static final String USER_READ = "auth:user:read";
    public static final String USER_CREATE = "auth:user:create";
    public static final String USER_UPDATE = "auth:user:update";
    public static final String USER_DELETE = "auth:user:delete";

    /** 本切片已落地的全部权限标识，种子数据据此建资源 */
    public static final List<String> ALL = List.of(
            TENANT_READ, TENANT_CREATE, TENANT_UPDATE, TENANT_DELETE,
            USER_READ, USER_CREATE, USER_UPDATE, USER_DELETE);

}
