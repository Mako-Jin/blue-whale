package com.bluewhale.mall.coupon.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mall.coupon.infrastructure.persistence.entity.CouponUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户券 Mapper。
 *
 * <p>核销与回退都走条件更新而不是「先查后改」：同一张券被两个下单请求
 * 同时选中时，只有一条 UPDATE 能命中 {@code status = 'UNUSED'}，
 * 另一条影响 0 行由调用方抛错。先查后改在并发下会让一张券付两笔订单。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface CouponUserMapper extends BaseMapper<CouponUserEntity> {

    /**
     * 核销：仅未使用的券可改为已使用。
     *
     * @param id      用户券主键
     * @param orderNo 订单号
     * @param now     核销时间
     * @return 影响行数；0 表示已被并发使用
     */
    @Update("UPDATE mall_coupon_user SET status = 'USED', order_no = #{orderNo}, used_time = #{now} "
            + "WHERE id = #{id} AND status = 'UNUSED' AND deleted = 0")
    int markUsed(@Param("id") Long id, @Param("orderNo") String orderNo, @Param("now") LocalDateTime now);

    /**
     * 回退：仅已使用的券可改回未使用。
     *
     * @param id 用户券主键
     * @return 影响行数
     */
    @Update("UPDATE mall_coupon_user SET status = 'UNUSED', order_no = NULL, used_time = NULL "
            + "WHERE id = #{id} AND status = 'USED' AND deleted = 0")
    int release(@Param("id") Long id);

    /**
     * 按订单号回退（订单取消或整单退款）。
     *
     * @param orderNo 订单号
     * @return 回退张数
     */
    @Update("UPDATE mall_coupon_user SET status = 'UNUSED', order_no = NULL, used_time = NULL "
            + "WHERE order_no = #{orderNo} AND status = 'USED' AND deleted = 0")
    int releaseByOrderNo(@Param("orderNo") String orderNo);
}
