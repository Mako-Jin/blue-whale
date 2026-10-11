package com.bluewhale.sts.auth.common.enums;

/**
 * 资源类型 —— 描述"这条资源是什么东西"。
 *
 * <p>取值取自参照实现 {@code SystemTerminator} 的 {@code ResourceTypeEnums}，
 * 但去掉了它的数字编码（原本是 0~9），改用可读字符串：
 * 数字编码一旦写进库，读表时完全看不出 {@code 6} 是 API 还是按钮。
 *
 * <p>{@link #isRequestScoped()} 用来回答"这条资源是否对应一次 HTTP 请求" ——
 * 只有 API 与 BUTTON 会出现在鉴权判定里；菜单/页面只影响前端可见性，
 * 不参与后端接口的准入判断。把这条判断收在枚举里，避免散落到各处写成 {@code if (type == 6)}。
 */
public enum ResourceType {

    /** 系统 */
    SYSTEM("SYSTEM", false),
    /** 服务 */
    SERVER("SERVER", false),
    /** 服务实例 */
    SERVICE("SERVICE", false),
    /** 模块 */
    MODULE("MODULE", false),
    /** 菜单 */
    MENU("MENU", false),
    /** 页面 */
    PAGE("PAGE", false),
    /** 接口 */
    API("API", true),
    /** 按钮 */
    BUTTON("BUTTON", true),
    /** 数据 */
    DATA("DATA", false);

    private final String wire;
    private final boolean requestScoped;

    ResourceType(String wire, boolean requestScoped) {
        this.wire = wire;
        this.requestScoped = requestScoped;
    }

    public String wireName() {
        return wire;
    }

    /** 是否对应一次 HTTP 请求（参与后端鉴权判定） */
    public boolean isRequestScoped() {
        return requestScoped;
    }

    public static ResourceType parse(String value) {
        if (value == null) {
            return null;
        }
        for (ResourceType type : values()) {
            if (type.wire.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }

}
