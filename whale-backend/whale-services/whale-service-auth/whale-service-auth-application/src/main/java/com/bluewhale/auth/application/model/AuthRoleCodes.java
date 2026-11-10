package com.bluewhale.auth.application.model;

/**
 * 认证域内置角色编码。
 *
 * @author BlueWhale
 *
 * TODO 这个是做什么的，就只配置了一个超级管理员的编码吗？
 */
public final class AuthRoleCodes {

    /** 内置超级管理员 */
    public static final String SUPER_ADMIN = "super_admin";

    /**
     * 私有构造器：常量类禁止实例化
     */
    private AuthRoleCodes() {
    }
}
