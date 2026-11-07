package com.bluewhale.mall.catalog.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SkuPriceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 价目 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SkuPriceMapper extends BaseMapper<SkuPriceEntity> {

    /**
     * 物理删除某 SKU 的全部价目。
     *
     * <p>价目是「配置」而非「单据」：它不参与任何历史追溯（订单里存的是成交价快照），
     * 每次保存都全量覆盖。这里必须物理删除——逻辑删除会占住
     * {@code (tenant, sku_id, price_type)} 唯一键，导致「删掉 B2B 价再加回来」直接撞键。</p>
     *
     * @param skuId SKU ID
     * @return 影响行数
     */
    @Delete("DELETE FROM mall_sku_price WHERE sku_id = #{skuId}")
    int physicalDeleteBySkuId(@Param("skuId") Long skuId);
}
