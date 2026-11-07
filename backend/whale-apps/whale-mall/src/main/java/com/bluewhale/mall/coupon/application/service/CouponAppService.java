package com.bluewhale.mall.coupon.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.coupon.application.command.CouponSaveCommand;
import com.bluewhale.mall.coupon.application.view.CouponUserView;
import com.bluewhale.mall.coupon.application.view.CouponView;
import com.bluewhale.mall.coupon.domain.model.CouponQuery;
import com.bluewhale.mall.coupon.domain.model.CouponUser;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券应用服务。
 *
 * <p>除运营侧模板维护与买家侧领券外，还对外提供三个<b>给订单域用</b>的能力：
 * {@link #requireUsable}、{@link #use}、{@link #releaseByOrderNo}。
 * 把它们放在这里而不是让订单服务直接操作券的仓储，是为了让「券能不能用」
 * 只有一处判断——两处判断迟早会给出不同答案。</p>
 *
 * @author BlueWhale
 */
public interface CouponAppService {

    /**
     * 券模板分页。
     *
     * @param query   查询条件
     * @param current 页码
     * @param size    每页条数
     * @param actor   操作人
     * @return 分页结果
     */
    PageResult<CouponView> page(CouponQuery query, Long current, Long size, MallActor actor);

    /**
     * 券模板详情。
     *
     * @param id    模板 ID
     * @param actor 操作人
     * @return 模板视图
     */
    CouponView detail(Long id, MallActor actor);

    /**
     * 新建券模板。
     *
     * @param command 保存命令
     * @param actor   操作人
     * @return 模板视图
     */
    CouponView create(CouponSaveCommand command, MallActor actor);

    /**
     * 编辑券模板（不影响已领出的券）。
     *
     * @param id      模板 ID
     * @param command 保存命令
     * @param actor   操作人
     * @return 模板视图
     */
    CouponView update(Long id, CouponSaveCommand command, MallActor actor);

    /**
     * 启停券模板。
     *
     * @param id     模板 ID
     * @param status 目标状态
     * @param actor  操作人
     */
    void changeStatus(Long id, String status, MallActor actor);

    /**
     * 买家领券。
     *
     * @param couponId 模板 ID
     * @param actor    操作人
     * @return 领到的券
     */
    CouponUserView claim(Long couponId, MallActor actor);

    /**
     * 我的券分页。
     *
     * @param status  状态过滤（UNUSED / USED / EXPIRED，空 = 全部）
     * @param current 页码
     * @param size    每页条数
     * @param actor   操作人
     * @return 分页结果
     */
    PageResult<CouponUserView> mine(String status, Long current, Long size, MallActor actor);

    /**
     * 当前订单可用的券（含不可用及原因），按可减金额降序。
     *
     * @param channelId   渠道 ID
     * @param goodsAmount 商品总额
     * @param actor       操作人
     * @return 券列表
     */
    List<CouponUserView> available(Long channelId, BigDecimal goodsAmount, MallActor actor);

    /**
     * 取出可用于本单的券，不可用直接抛对应业务异常。
     *
     * @param couponUserId 用户券 ID
     * @param userId       下单用户 ID
     * @param channelId    渠道 ID
     * @param goodsAmount  商品总额
     * @return 用户券
     */
    CouponUser requireUsable(Long couponUserId, Long userId, Long channelId, BigDecimal goodsAmount);

    /**
     * 核销券（条件更新，已被并发使用则抛错）。
     *
     * @param couponUserId 用户券 ID
     * @param orderNo      订单号
     */
    void use(Long couponUserId, String orderNo);

    /**
     * 按订单号回退券（订单取消或整单退款）。
     *
     * @param orderNo 订单号
     * @return 回退张数
     */
    int releaseByOrderNo(String orderNo);
}
