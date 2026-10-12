package com.bluewhale.sts.auth.common.enums;

/**
 * 口令的变更来源 —— 记录"这个口令是什么情况下设进来的"。
 *
 * <p>只承载证据价值：排查"某账号的口令为什么是现在这个"时，
 * 能区分"用户自己设的"与"管理员重置的"，而这在安全事件里往往是第一个要回答的问题。
 * 它**不参与**任何判定 —— 防重用检查对所有来源一视同仁。
 *
 * <p><b>刻意只登记当前真实存在的两个入口</b>（建号、管理员重置）。
 * 参照实现 {@code SystemTerminator} 的 {@code PasswordChangeSourceEnums} 列了更多来源，
 * 但其中多数对应的功能（自助注册、忘记口令、强制过期改密）在本仓尚未落地 ——
 * 预置用不到的枚举值，与"模块按需创建，不预置空壳"是同一条原则。
 */
public enum PasswordChangeSource {

    /** 建号时设置的初始口令 */
    CREATE("CREATE", "建号设置"),

    /** 管理员重置他人口令 */
    ADMIN_RESET("ADMIN_RESET", "管理员重置");

    private final String wire;
    private final String label;

    PasswordChangeSource(String wire, String label) {
        this.wire = wire;
        this.label = label;
    }

    public String wireName() {
        return wire;
    }

    public String getLabel() {
        return label;
    }

}
