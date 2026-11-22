package com.bluewhale.pay.enums;

/**
 * 管理端对账差异处置动作。
 *
 * <p>动作与目标处置状态的映射固定为：
 * {@link #CONFIRM} → {@link PayReconcileHandleStatus#CONFIRMED}、
 * {@link #COMPENSATE} → {@link PayReconcileHandleStatus#COMPENSATED}、
 * {@link #SUSPEND} → {@link PayReconcileHandleStatus#SUSPENDED}。</p>
 *
 * @author BlueWhale
 */
public enum PayReconcileAction {

    /** 确认本地无误 */
    CONFIRM("CONFIRM", PayReconcileHandleStatus.CONFIRMED),

    /** 以渠道为准补偿（仅 STATUS_MISMATCH） */
    COMPENSATE("COMPENSATE", PayReconcileHandleStatus.COMPENSATED),

    /** 暂不处理 */
    SUSPEND("SUSPEND", PayReconcileHandleStatus.SUSPENDED);

    private final String code;

    private final PayReconcileHandleStatus targetStatus;

    /**
     * 动作构造
     *
     * @param code         动作码
     * @param targetStatus 处置成功后的目标状态
     */
    PayReconcileAction(String code, PayReconcileHandleStatus targetStatus) {
        this.code = code;
        this.targetStatus = targetStatus;
    }

    /**
     * 动作码
     *
     * @return 动作字符串
     */
    public String code() {
        return code;
    }

    /**
     * 处置成功后的目标状态
     *
     * @return 目标处置状态
     */
    public PayReconcileHandleStatus targetStatus() {
        return targetStatus;
    }

    /**
     * 按动作码解析枚举（大小写敏感，入参先由调用方归一化）
     *
     * @param code 动作码
     * @return 匹配的枚举，未知返回 {@code null}
     */
    public static PayReconcileAction fromCode(String code) {
        for (PayReconcileAction action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        return null;
    }
}
