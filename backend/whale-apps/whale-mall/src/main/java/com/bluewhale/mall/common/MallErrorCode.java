package com.bluewhale.mall.common;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * 商城业务错误码。
 *
 * <p>段位约定：{@code 45xxx} 归 {@code whale-mall}
 * （42xxx=lms、43xxx=party、44xxx=map，45xxx 全段空闲）。
 * 45000–45009 为身份与类目；45010–45019 为商品 / SKU / 价目；45020–45029 为库存、
 * 渠道与购物车；45030–45049 为订单与支付回调；45050 起留给售后与促销。</p>
 *
 * @author BlueWhale
 */
public final class MallErrorCode {

    /** 缺少可信操作人身份，写操作失败关闭 */
    public static final int ACTOR_REQUIRED = 45000;

    /** 业务编号取号失败 */
    public static final int SEQUENCE_INVALID = 45001;

    /** 类目不存在或无权访问（跨租户一律按不存在处理） */
    public static final int CATEGORY_NOT_FOUND = 45002;

    /** 同级类目重名 */
    public static final int CATEGORY_NAME_CONFLICT = 45003;

    /** 类目当前状态不允许该操作（含子类目或商品时不可删除） */
    public static final int CATEGORY_STATE_CONFLICT = 45004;

    /** 类目字段非法（名称或层级越界） */
    public static final int CATEGORY_INVALID = 45005;

    /** 商品不存在或无权访问 */
    public static final int PRODUCT_NOT_FOUND = 45010;

    /** 商品编码已存在 */
    public static final int PRODUCT_CODE_CONFLICT = 45011;

    /** 商品当前状态不允许该操作 */
    public static final int PRODUCT_STATE_CONFLICT = 45012;

    /** 商品字段非法（名称 / 图片 / 明细越界） */
    public static final int PRODUCT_INVALID = 45013;

    /** SKU 不存在或无权访问 */
    public static final int SKU_NOT_FOUND = 45014;

    /** SKU 编码全局重复 */
    public static final int SKU_CODE_CONFLICT = 45015;

    /** 未解析到可用价目（该渠道模式要求的价目缺失） */
    public static final int PRICE_MISSING = 45016;

    /** 价目非法（非正数或积分价带小数） */
    public static final int PRICE_INVALID = 45017;

    /** 库存记录不存在 */
    public static final int STOCK_NOT_FOUND = 45020;

    /** 可售库存不足 */
    public static final int STOCK_INSUFFICIENT = 45021;

    /** 库存调整参数非法 */
    public static final int STOCK_INVALID = 45022;

    /** 商城渠道不存在或无权访问 */
    public static final int CHANNEL_NOT_FOUND = 45023;

    /** 渠道编码已存在 */
    public static final int CHANNEL_CODE_CONFLICT = 45024;

    /** 渠道当前状态不允许该操作 */
    public static final int CHANNEL_STATE_CONFLICT = 45025;

    /** 渠道字段非法（编码 / 名称 / 模式越界） */
    public static final int CHANNEL_INVALID = 45026;

    /** 购物车条目不存在或不属于当前用户 */
    public static final int CART_ITEM_NOT_FOUND = 45027;

    /** 购物车数量非法（须为正整数且不超过上限） */
    public static final int CART_QUANTITY_INVALID = 45028;

    /** 购物车为空或未选中任何商品 */
    public static final int CART_EMPTY = 45029;

    /** 订单不存在或无权访问 */
    public static final int ORDER_NOT_FOUND = 45030;

    /** 订单当前状态不允许该操作 */
    public static final int ORDER_STATE_CONFLICT = 45031;

    /** 下单幂等键缺失 */
    public static final int ORDER_IDEMPOTENCY_REQUIRED = 45032;

    /** 订单明细行数超过上限 */
    public static final int ORDER_LINES_EXCEEDED = 45033;

    /** 订单金额非法 */
    public static final int ORDER_AMOUNT_INVALID = 45034;

    /** 无权操作该订单（非本人且非管理员） */
    public static final int ORDER_FORBIDDEN = 45035;

    /** 订单中的商品已下架或渠道已停用 */
    public static final int ORDER_PRODUCT_UNAVAILABLE = 45036;

    /** 支付回调参数非法（订单号 / 支付单号缺失或金额不匹配） */
    public static final int PAY_CALLBACK_INVALID = 45037;

    /** 业务错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(ACTOR_REQUIRED, "缺少操作人身份，无法执行该操作"),
            Map.entry(SEQUENCE_INVALID, "业务编号生成失败，请检查编号规则"),
            Map.entry(CATEGORY_NOT_FOUND, "类目不存在或无权访问"),
            Map.entry(CATEGORY_NAME_CONFLICT, "同级下已存在同名类目"),
            Map.entry(CATEGORY_STATE_CONFLICT, "类目下仍有子类目或商品，无法删除"),
            Map.entry(CATEGORY_INVALID, "类目信息不合法，请检查名称与上级类目"),
            Map.entry(PRODUCT_NOT_FOUND, "商品不存在或无权访问"),
            Map.entry(PRODUCT_CODE_CONFLICT, "商品编码已存在"),
            Map.entry(PRODUCT_STATE_CONFLICT, "商品当前状态不允许该操作"),
            Map.entry(PRODUCT_INVALID, "商品信息不合法，请检查名称、图片与规格"),
            Map.entry(SKU_NOT_FOUND, "商品规格不存在或无权访问"),
            Map.entry(SKU_CODE_CONFLICT, "SKU 编码已存在，请更换"),
            Map.entry(PRICE_MISSING, "该商品在当前渠道下未配置可售价目"),
            Map.entry(PRICE_INVALID, "价目不合法：须为正数，积分价必须为整数"),
            Map.entry(STOCK_NOT_FOUND, "库存记录不存在"),
            Map.entry(STOCK_INSUFFICIENT, "可售库存不足"),
            Map.entry(STOCK_INVALID, "库存调整参数不合法"),
            Map.entry(CHANNEL_NOT_FOUND, "商城渠道不存在或无权访问"),
            Map.entry(CHANNEL_CODE_CONFLICT, "渠道编码已存在"),
            Map.entry(CHANNEL_STATE_CONFLICT, "渠道当前状态不允许该操作"),
            Map.entry(CHANNEL_INVALID, "渠道信息不合法，请检查编码、名称与模式"),
            Map.entry(CART_ITEM_NOT_FOUND, "购物车条目不存在"),
            Map.entry(CART_QUANTITY_INVALID, "购买数量不合法"),
            Map.entry(CART_EMPTY, "购物车为空或未选中商品"),
            Map.entry(ORDER_NOT_FOUND, "订单不存在或无权访问"),
            Map.entry(ORDER_STATE_CONFLICT, "订单当前状态不允许该操作"),
            Map.entry(ORDER_IDEMPOTENCY_REQUIRED, "下单缺少幂等键，请刷新后重试"),
            Map.entry(ORDER_LINES_EXCEEDED, "单笔订单商品种类超出上限"),
            Map.entry(ORDER_AMOUNT_INVALID, "订单金额不合法"),
            Map.entry(ORDER_FORBIDDEN, "无权操作该订单"),
            Map.entry(ORDER_PRODUCT_UNAVAILABLE, "订单中部分商品已下架或渠道已停用"),
            Map.entry(PAY_CALLBACK_INVALID, "支付回调参数不合法"));

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private MallErrorCode() {
    }

    /**
     * 以错误码构造业务异常。
     *
     * @param code 业务错误码
     * @return 业务异常
     */
    public static BizException exception(int code) {
        return new BizException(code, messageOf(code));
    }

    /**
     * 以错误码与自定义消息构造业务异常。
     *
     * @param code 业务错误码
     * @param message 自定义提示语
     * @return 业务异常
     */
    public static BizException exception(int code, String message) {
        return new BizException(code, message);
    }

    /**
     * 错误码默认提示语。
     *
     * @param code 业务错误码
     * @return 提示语
     */
    private static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "商城业务校验失败");
    }
}
