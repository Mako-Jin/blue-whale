package com.bluewhale.mall.catalog.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SpuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SpuMapper extends BaseMapper<SpuEntity> {

    /**
     * 逻辑删除并将编码改写为 {@code 原编码#D{id}}。
     *
     * <p>软删除 + 唯一键的标准解法：不改写编码，删掉再建同编码会撞唯一键，
     * 表现为「编码看起来没被占用却建不进去」。</p>
     *
     * @param id 商品 ID
     * @param spuCode 改写后的编码
     * @return 影响行数
     */
    @Update("UPDATE mall_spu SET spu_code = #{spuCode}, deleted = 1 WHERE id = #{id}")
    int softDeleteWithCode(@Param("id") Long id, @Param("spuCode") String spuCode);
}
