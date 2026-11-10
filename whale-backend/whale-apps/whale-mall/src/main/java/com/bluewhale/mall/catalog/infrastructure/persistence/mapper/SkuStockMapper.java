package com.bluewhale.mall.catalog.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SkuStockEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 库存 Mapper。
 *
 * <p>所有变动都是<b>一条带条件的 UPDATE</b>：把「判断够不够」和「扣减」放进同一条语句，
 * 由数据库保证原子性。先 SELECT 再 UPDATE 在并发下必然超卖，而且不会报错——
 * 只会发不出货。</p>
 *
 * <p>租户条件由行级隔离插件自动追加，这里不重复写 {@code tenant_id}。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface SkuStockMapper extends BaseMapper<SkuStockEntity> {

    /**
     * 预占：可售减、预占增（可售不足时不动任何行）。
     *
     * @param skuId SKU ID
     * @param quantity 数量
     * @return 影响行数；0 表示库存不足
     */
    @Update("UPDATE mall_sku_stock SET available_qty = available_qty - #{quantity}, "
            + "reserved_qty = reserved_qty + #{quantity} "
            + "WHERE sku_id = #{skuId} AND deleted = 0 AND available_qty >= #{quantity}")
    int reserve(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    /**
     * 释放预占：可售增、预占减。
     *
     * @param skuId SKU ID
     * @param quantity 数量
     * @return 影响行数；0 表示预占不足（理论上不会发生，说明数据已不一致）
     */
    @Update("UPDATE mall_sku_stock SET available_qty = available_qty + #{quantity}, "
            + "reserved_qty = reserved_qty - #{quantity} "
            + "WHERE sku_id = #{skuId} AND deleted = 0 AND reserved_qty >= #{quantity}")
    int release(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    /**
     * 出库：预占减（可售在下单时已扣，这里不能再扣一次）。
     *
     * @param skuId SKU ID
     * @param quantity 数量
     * @return 影响行数
     */
    @Update("UPDATE mall_sku_stock SET reserved_qty = reserved_qty - #{quantity} "
            + "WHERE sku_id = #{skuId} AND deleted = 0 AND reserved_qty >= #{quantity}")
    int ship(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    /**
     * 撤回出库：预占加回（发货单作废时）。
     *
     * <p>可售数量不动：下单预占时已经扣过，这里再加回去会凭空多出一件可售库存。</p>
     *
     * @param skuId SKU ID
     * @param quantity 数量
     * @return 影响行数
     */
    @Update("UPDATE mall_sku_stock SET reserved_qty = reserved_qty + #{quantity} "
            + "WHERE sku_id = #{skuId} AND deleted = 0")
    int unship(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    /**
     * 运营调整可售数量（增量，调整后不得为负）。
     *
     * @param skuId SKU ID
     * @param delta 增量，可为负
     * @return 影响行数
     */
    @Update("UPDATE mall_sku_stock SET available_qty = available_qty + #{delta} "
            + "WHERE sku_id = #{skuId} AND deleted = 0 AND available_qty + #{delta} >= 0")
    int adjust(@Param("skuId") Long skuId, @Param("delta") int delta);

    /**
     * 查询调整后的可售数量。
     *
     * @param skuId SKU ID
     * @return 可售数量；不存在返回 null
     */
    @Select("SELECT available_qty FROM mall_sku_stock WHERE sku_id = #{skuId} AND deleted = 0")
    Integer selectAvailable(@Param("skuId") Long skuId);
}
