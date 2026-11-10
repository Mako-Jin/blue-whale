package com.bluewhale.iot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.iot.entity.IotThingModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * IoT 物模型 Mapper。
 *
 * <p>整包覆盖式保存需要<b>物理删除</b>（绕过逻辑删除）——物模型行无逐行审计需求，
 * 且唯一键 {@code uk_product_kind_identifier} 会被逻辑删除残留行占位，
 * 导致"删掉再加"撞键（对齐 whale-mall 商品/类目的处理教训）。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface IotThingModelMapper extends BaseMapper<IotThingModel> {

    /**
     * 物理删除产品下全部物模型行（整包覆盖式保存前调用）。
     *
     * @param productId 产品ID
     * @return 删除行数
     */
    @Delete("DELETE FROM iot_thing_model WHERE product_id = #{productId}")
    int deletePhysicalByProductId(@Param("productId") Long productId);
}
