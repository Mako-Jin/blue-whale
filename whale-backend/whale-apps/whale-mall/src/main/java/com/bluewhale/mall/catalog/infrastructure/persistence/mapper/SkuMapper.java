package com.bluewhale.mall.catalog.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SkuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * SKU Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SkuMapper extends BaseMapper<SkuEntity> {

    /**
     * 逻辑删除并将编码改写为 {@code 原编码#D{id}}。
     *
     * <p>软删除 + 唯一键的标准解法：不改写编码，删掉再建同编码会撞唯一键，
     * 表现为「编码看起来没被占用却建不进去」。</p>
     *
     * @param id SKU ID
     * @param skuCode 改写后的编码
     * @return 影响行数
     */
    @Update("UPDATE mall_sku SET sku_code = #{skuCode}, deleted = 1 WHERE id = #{id}")
    int softDeleteWithCode(@Param("id") Long id, @Param("skuCode") String skuCode);
}
