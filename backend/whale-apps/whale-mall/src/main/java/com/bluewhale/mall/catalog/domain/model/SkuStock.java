package com.bluewhale.mall.catalog.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SKU 可售库存。
 *
 * <p>两个数量分工明确：{@link #availableQty} 是「还能卖多少」，下单即扣；
 * {@link #reservedQty} 是「已经卖了但还没发」，下单即加、发货才减。</p>
 *
 * <p><b>扣减不在这个对象里做</b>：真正的扣减是数据库的条件更新
 * （{@code WHERE available_qty >= ?}），先在内存里判断再写回，并发下必然超卖，
 * 而且超卖不会抛异常——只会发不出货。这里只做「这次要扣的量是否合法」的校验。</p>
 *
 * @author BlueWhale
 */
@Data
public class SkuStock {

    /** SKU ID */
    private Long skuId;

    /** 可售数量 */
    private Integer availableQty;

    /** 已预占数量（下单未发货） */
    private Integer reservedQty;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 构造空库存（新建 SKU 时使用）。
     *
     * @param skuId SKU ID
     * @return 可售与预占均为 0 的库存
     */
    public static SkuStock empty(Long skuId) {
        SkuStock stock = new SkuStock();
        stock.setSkuId(skuId);
        stock.setAvailableQty(0);
        stock.setReservedQty(0);
        return stock;
    }

    /**
     * 校验数量为正。
     *
     * @param quantity 数量
     */
    public static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw MallErrorCode.exception(MallErrorCode.STOCK_INVALID, "库存变动数量必须大于 0");
        }
    }

    /**
     * 可售数量是否足够。
     *
     * @param quantity 需要的数量
     * @return 足够返回 true
     */
    public boolean isEnough(int quantity) {
        return availableQty != null && availableQty >= quantity;
    }

    /**
     * 校验可售数量足够，不足时抛业务异常。
     *
     * @param quantity 需要的数量
     * @param skuCode SKU 编码（用于提示）
     */
    public void assertEnough(int quantity, String skuCode) {
        if (!isEnough(quantity)) {
            throw MallErrorCode.exception(MallErrorCode.STOCK_INSUFFICIENT,
                    "SKU " + skuCode + " 库存不足，当前可售 " + (availableQty == null ? 0 : availableQty));
        }
    }

    /**
     * 在库总量（可售 + 预占），用于运营盘点展示。
     *
     * @return 在库总量
     */
    public int totalQty() {
        int available = availableQty == null ? 0 : availableQty;
        int reserved = reservedQty == null ? 0 : reservedQty;
        return available + reserved;
    }
}
