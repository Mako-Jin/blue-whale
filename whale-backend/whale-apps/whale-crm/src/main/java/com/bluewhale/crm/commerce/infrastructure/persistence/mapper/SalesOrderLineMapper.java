package com.bluewhale.crm.commerce.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.SalesOrderLineEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商务订单行 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SalesOrderLineMapper extends BaseMapper<SalesOrderLineEntity> {

    /**
     * 按订单 ID 物理删除订单行。
     *
     * <p>行表挂 BaseEntity 的 {@code @TableLogic}，BaseMapper.delete 走逻辑删除（deleted=1），
     * 旧行仍物理存在，会顶住 {@code uk_crm_sales_order_line_no}（不含 deleted 列）唯一键，
     * 导致 replaceLines 重插冲突；明细行随主单留痕即可，这里用原生 SQL 物理删除（含历史残留行）。</p>
     *
     * @param orderId 订单 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM crm_sales_order_line WHERE order_id = #{orderId}")
    int deletePhysicallyByOrderId(@Param("orderId") Long orderId);
}
