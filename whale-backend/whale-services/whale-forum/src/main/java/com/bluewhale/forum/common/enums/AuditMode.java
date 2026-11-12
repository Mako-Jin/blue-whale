package com.bluewhale.forum.common.enums;

/**
 * 版块审核模式（对应 {@code fm_board.audit_mode}）。
 *
 * @author BlueWhale
 */
public enum AuditMode {

    /** 先发后审：直接发布，命中举报或规则后复检 */
    POST,

    /** 先审后发：提交进 pending，通过后才可被他人看见 */
    PRE;

    /**
     * 解析库值（0/1 或枚举名）。
     *
     * @param value 库值或枚举名
     * @return 审核模式，未知/空值按 POST（不阻断社区活跃度）
     */
    public static AuditMode of(String value) {
        if (value == null || value.isBlank()) {
            return POST;
        }
        String normalized = value.trim();
        if ("1".equals(normalized) || "PRE".equalsIgnoreCase(normalized)) {
            return PRE;
        }
        return POST;
    }
}
