package com.bluewhale.pay.enums;

/**
 * 支付单状态（{@code pay_order.status} 的唯一取值来源）。
 *
 * <p>替代散落在 service/channel 中的字符串常量（审计 P2）：状态流转与比较改为
 * 枚举后，拼写错误在编译期暴露；{@link #code()} 即落库字符串，与历史数据兼容。</p>
 *
 * <p>流转方向单向：{@code CREATED → PAYING → PAID}、{@code PAYING → CREATED}（渠道失败回退）、
 * {@code CREATED → CANCELLED}、{@code PAID → REFUNDED}。</p>
 *
 * <p>{@link #PAYING} 是「渠道调用中」的中间态：抢占成功后先提交，再在事务外调用渠道，
 * 避免渠道扣款成功而本地事务回滚。订单停在 PAYING 说明渠道结果未落账，需走对账确认。</p>
 *
 * @author BlueWhale
 */
public enum PayOrderStatus {

    /** 待支付（创建后的初始状态） */
    CREATED("CREATED"),

    /** 支付中（已抢占，渠道调用尚未落账） */
    PAYING("PAYING"),

    /** 已支付 */
    PAID("PAID"),

    /** 已取消 */
    CANCELLED("CANCELLED"),

    /** 已退款 */
    REFUNDED("REFUNDED");

    private final String code;

    /**
     * 状态码构造
     *
     * @param code 状态码（与库中存储值一致）
     */
    PayOrderStatus(String code) {
        this.code = code;
    }

    /**
     * 落库与本系统内部比较使用的状态码
     *
     * @return 状态字符串（与枚举名一致）
     */
    public String code() {
        return code;
    }

    /**
     * 按状态码解析枚举（大小写敏感，与库中数据一致）
     *
     * @param code 状态码
     * @return 匹配的枚举，未知返回 {@code null}
     */
    public static PayOrderStatus fromCode(String code) {
        for (PayOrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
