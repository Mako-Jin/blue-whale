package com.bluewhale.sts.auth.common.enums;

/**
 * 授权效果 —— 角色与资源之间是"允许"还是"显式拒绝"。
 *
 * <p><b>为什么要 DENY：</b>只有 ALLOW 时，权限只能做加法 ——
 * 想让某个人在"能进全部门"的基础上排除一个特定资源，只能靠不授予，
 * 而授予往往来自上级角色的继承，排除不掉。有了 DENY，"排除"成为可表达的动作。
 *
 * <p>判定时的优先级：**DENY 优先于 ALLOW**。即只要命中任意一条 DENY，
 * 即便同时命中 ALLOW 也判定为拒绝 —— 这与主流 IAM 系统一致，
 * 也让"收紧权限"始终是安全方向的操作。
 */
public enum GrantEffect {

    ALLOW("ALLOW"),
    DENY("DENY");

    private final String wire;

    GrantEffect(String wire) {
        this.wire = wire;
    }

    public String wireName() {
        return wire;
    }

    public static GrantEffect parse(String value) {
        if (value == null) {
            return null;
        }
        for (GrantEffect effect : values()) {
            if (effect.wire.equalsIgnoreCase(value.trim())) {
                return effect;
            }
        }
        return null;
    }

}
