package com.bluewhale.mall.coupon.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.coupon.infrastructure.persistence.entity.CouponEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 优惠券模板 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface CouponMapper extends BaseMapper<CouponEntity> {

    /**
     * 发行量占用 +1（条件更新：不限量或尚有余额）。
     *
     * @param id 模板主键
     * @return 影响行数；0 表示已发完
     */
    @Update("UPDATE mall_coupon SET issued_qty = issued_qty + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND (total_qty = 0 OR issued_qty < total_qty)")
    int occupyIssuedQty(@Param("id") Long id);
}
