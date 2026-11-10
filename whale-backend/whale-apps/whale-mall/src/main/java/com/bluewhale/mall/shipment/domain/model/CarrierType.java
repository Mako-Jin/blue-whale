package com.bluewhale.mall.shipment.domain.model;

/**
 * 承运商。
 *
 * <p>枚举只做「已知」与「其它」的区分，不追求覆盖所有快递公司：真实场景里
 * 总有没列进来的承运商，{@link #OTHER} 让运营能填一个自定名字兜住，
 * 而不是逼着开发每次加一行枚举。</p>
 *
 * <p>{@link #SELF} 是商家自送（同城配送、员工代送），仍要求填运单号——
 * 内部单号也算，因为「没有运单号的发货单」等于没有凭证。</p>
 *
 * @author BlueWhale
 */
public enum CarrierType {

    /** 顺丰速运 */
    SF("顺丰速运"),

    /** 圆通速递 */
    YTO("圆通速递"),

    /** 中通快递 */
    ZTO("中通快递"),

    /** 申通快递 */
    STO("申通快递"),

    /** 韵达速递 */
    YD("韵达速递"),

    /** 京东物流 */
    JD("京东物流"),

    /** 中国邮政 EMS */
    EMS("中国邮政EMS"),

    /** 德邦快递 */
    DBL("德邦快递"),

    /** 商家自送 */
    SELF("商家自送"),

    /** 其它（运营填自定名称） */
    OTHER("其它");

    /** 默认展示名称 */
    private final String label;

    /**
     * 构造承运商枚举。
     *
     * @param label 默认展示名称
     */
    CarrierType(String label) {
        this.label = label;
    }

    /**
     * 默认展示名称。
     *
     * @return 名称
     */
    public String label() {
        return label;
    }
}
