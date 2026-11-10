package com.bluewhale.mall.coupon.domain.repository;

import com.bluewhale.mall.coupon.domain.model.CouponUser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户券仓储。
 *
 * @author BlueWhale
 */
public interface CouponUserRepository {

    /**
     * 新增用户券。
     *
     * @param coupon 用户券
     * @return 主键
     */
    Long insert(CouponUser coupon);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 用户券
     */
    Optional<CouponUser> findById(Long id);

    /**
     * 某用户已持有某张券的数量（用于限领校验与领取序号）。
     *
     * @param userId   用户 ID
     * @param couponId 模板 ID
     * @return 已领取张数
     */
    int countByUser(Long userId, Long couponId);

    /**
     * 我的券分页。
     *
     * @param userId 用户 ID
     * @param status 状态过滤（空 = 全部）
     * @param offset 偏移量
     * @param size   条数
     * @return 券列表
     */
    List<CouponUser> pageMine(Long userId, String status, long offset, int size);

    /**
     * 统计我的券数量。
     *
     * @param userId 用户 ID
     * @param status 状态过滤
     * @return 总数
     */
    long countMine(Long userId, String status);

    /**
     * 查询某用户当前未使用且在有效期内的券。
     *
     * @param userId 用户 ID
     * @param now    当前时间
     * @return 券列表
     */
    List<CouponUser> listUsable(Long userId, LocalDateTime now);

    /**
     * 核销（条件更新：仅 UNUSED 可改为 USED）。
     *
     * @param id      用户券主键
     * @param orderNo 订单号
     * @param now     核销时间
     * @return 影响行数；0 表示已被并发使用
     */
    int markUsed(Long id, String orderNo, LocalDateTime now);

    /**
     * 回退（条件更新：仅 USED 可改回 UNUSED）。
     *
     * @param id 用户券主键
     * @return 影响行数
     */
    int release(Long id);

    /**
     * 按订单号回退全部券（订单取消/整单退款时用）。
     *
     * @param orderNo 订单号
     * @return 回退张数
     */
    int releaseByOrderNo(String orderNo);
}
