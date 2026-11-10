package com.bluewhale.mall.coupon.domain.repository;

import com.bluewhale.mall.coupon.domain.model.Coupon;
import com.bluewhale.mall.coupon.domain.model.CouponQuery;

import java.util.List;
import java.util.Optional;

/**
 * 优惠券模板仓储。
 *
 * @author BlueWhale
 */
public interface CouponRepository {

    /**
     * 分页查询券模板。
     *
     * @param query  查询条件
     * @param offset 偏移量
     * @param size   条数
     * @return 模板列表
     */
    List<Coupon> page(CouponQuery query, long offset, int size);

    /**
     * 统计券模板数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(CouponQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 模板
     */
    Optional<Coupon> findById(Long id);

    /**
     * 编码是否已存在。
     *
     * @param couponCode 券编码
     * @param excludeId  排除的主键（修改时排除自身）
     * @return 已存在返回 true
     */
    boolean existsCode(String couponCode, Long excludeId);

    /**
     * 新增或更新。
     *
     * @param coupon 模板
     * @return 主键
     */
    Long save(Coupon coupon);

    /**
     * 发行量占用 +1。
     *
     * <p>条件更新：不限量（{@code total_qty = 0}）或尚有余额才 +1，
     * 影响 0 行表示已发完。先查后改在并发领取下必然超发。</p>
     *
     * @param id 模板主键
     * @return 占用成功返回 true
     */
    boolean occupyIssuedQty(Long id);

    /**
     * 更新状态。
     *
     * @param id     主键
     * @param status 状态
     */
    void updateStatus(Long id, String status);
}
