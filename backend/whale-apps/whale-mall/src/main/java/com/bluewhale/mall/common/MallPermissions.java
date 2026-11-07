package com.bluewhale.mall.common;

/**
 * 商城权限码常量（与 {@code migration-5.94-mall.sql}、{@code migration-5.95-mall-m1.sql}、
 * {@code migration-5.96-mall-aftersale.sql} 登记保持一致）。
 *
 * <p>号段 **2200–2217**：2100–2108 归邮箱、2109 归邮件规则、2110–2115 归地图，
 * 故本服务从 2200 起。<b>不挪号</b>——权限号一旦发布就进了各租户的角色授权表，
 * 重排意味着线上授权全部错配。</p>
 *
 * <p>买家自助动作（购物车、下单、查自己的订单、申请售后）与运营动作（建商品、发货、审核售后）
 * 分开：前者给全员，后者只给运营角色。合成一个权限点会让「能买东西」等价于「能改商品」。</p>
 *
 * @author BlueWhale
 */
public final class MallPermissions {

    /** 商城工作台与渠道浏览（2200） */
    public static final String BROWSE = "sys:mall:browse";

    /** 类目查看（2201） */
    public static final String CATEGORY_LIST = "sys:mall:category:list";

    /** 类目维护：新建 / 编辑 / 删除（2202） */
    public static final String CATEGORY_MANAGE = "sys:mall:category:manage";

    /** 商品查看（2203） */
    public static final String PRODUCT_LIST = "sys:mall:product:list";

    /** 商品维护：新建 / 编辑 SKU 与价目（2204） */
    public static final String PRODUCT_MANAGE = "sys:mall:product:manage";

    /** 商品上下架（2205） */
    public static final String PRODUCT_PUBLISH = "sys:mall:product:publish";

    /** 库存查看（2206） */
    public static final String STOCK_LIST = "sys:mall:stock:list";

    /** 库存调整（2207） */
    public static final String STOCK_MANAGE = "sys:mall:stock:manage";

    /** 渠道查看（2208） */
    public static final String CHANNEL_LIST = "sys:mall:channel:list";

    /** 渠道维护：新建 / 启停（2209） */
    public static final String CHANNEL_MANAGE = "sys:mall:channel:manage";

    /** 买家自助：购物车与下单（2210，全员） */
    public static final String CART_MANAGE = "sys:mall:cart:manage";

    /** 订单查看：本人订单 + 运营列表（2211） */
    public static final String ORDER_LIST = "sys:mall:order:list";

    /** 订单运营：发货 / 完成（2212） */
    public static final String ORDER_MANAGE = "sys:mall:order:manage";

    /** 订单取消（本人或运营）（2213） */
    public static final String ORDER_CANCEL = "sys:mall:order:cancel";

    /** 整单退款（商城运营，2214） */
    public static final String ORDER_REFUND = "sys:mall:order:refund";

    /** 售后查看：本人售后单（2215，全员） */
    public static final String AFTER_SALE_APPLY = "sys:mall:aftersale:apply";

    /** 售后列表查看（2216，商城运营） */
    public static final String AFTER_SALE_LIST = "sys:mall:aftersale:list";

    /** 售后审核：通过 / 驳回（2217，商城运营） */
    public static final String AFTER_SALE_AUDIT = "sys:mall:aftersale:audit";


    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private MallPermissions() {
    }
}
