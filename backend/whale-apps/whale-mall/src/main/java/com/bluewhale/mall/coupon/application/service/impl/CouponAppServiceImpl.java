package com.bluewhale.mall.coupon.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mall.common.MallActor;
import com.bluewhale.mall.common.MallErrorCode;
import com.bluewhale.mall.config.MallProperties;
import com.bluewhale.mall.coupon.application.command.CouponSaveCommand;
import com.bluewhale.mall.coupon.application.service.CouponAppService;
import com.bluewhale.mall.coupon.application.view.CouponUserView;
import com.bluewhale.mall.coupon.application.view.CouponView;
import com.bluewhale.mall.coupon.domain.model.Coupon;
import com.bluewhale.mall.coupon.domain.model.CouponQuery;
import com.bluewhale.mall.coupon.domain.model.CouponStatus;
import com.bluewhale.mall.coupon.domain.model.CouponType;
import com.bluewhale.mall.coupon.domain.model.CouponUsability;
import com.bluewhale.mall.coupon.domain.model.CouponUser;
import com.bluewhale.mall.coupon.domain.repository.CouponRepository;
import com.bluewhale.mall.coupon.domain.repository.CouponUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 优惠券应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CouponAppServiceImpl implements CouponAppService {

    private final CouponRepository couponRepository;

    private final CouponUserRepository couponUserRepository;

    private final MallProperties properties;

    @Override
    public PageResult<CouponView> page(CouponQuery query, Long current, Long size, MallActor actor) {
        actor.requireUserId();
        long pageSize = pageSize(size);
        long pageNo = pageNo(current);
        CouponQuery condition = query == null ? new CouponQuery() : query;
        List<Coupon> coupons = couponRepository.page(condition, (pageNo - 1L) * pageSize, (int) pageSize);
        List<CouponView> views = new ArrayList<>(coupons.size());
        for (Coupon coupon : coupons) {
            views.add(toView(coupon));
        }
        return PageResult.of(views, couponRepository.count(condition), pageNo, pageSize);
    }

    @Override
    public CouponView detail(Long id, MallActor actor) {
        actor.requireUserId();
        return toView(requireTemplate(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponView create(CouponSaveCommand command, MallActor actor) {
        actor.requireUserId();
        Coupon coupon = new Coupon();
        apply(coupon, command);
        coupon.validate();
        if (couponRepository.existsCode(coupon.getCouponCode(), null)) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_CODE_CONFLICT);
        }
        couponRepository.save(coupon);
        return toView(coupon);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponView update(Long id, CouponSaveCommand command, MallActor actor) {
        actor.requireUserId();
        Coupon coupon = requireTemplate(id);
        apply(coupon, command);
        coupon.validate();
        if (couponRepository.existsCode(coupon.getCouponCode(), id)) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_CODE_CONFLICT);
        }
        couponRepository.save(coupon);
        return toView(coupon);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status, MallActor actor) {
        actor.requireUserId();
        Coupon coupon = requireTemplate(id);
        couponRepository.updateStatus(coupon.getId(), CouponStatus.parse(status).name());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponUserView claim(Long couponId, MallActor actor) {
        Long userId = actor.requireUserId();
        Coupon template = requireTemplate(couponId);
        LocalDateTime now = LocalDateTime.now();
        if (!template.claimable(now)) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_STATE_INVALID, "该优惠券当前不可领取");
        }
        int owned = couponUserRepository.countByUser(userId, couponId);
        int limit = template.getPerUserLimit() == null ? 1 : template.getPerUserLimit();
        if (owned >= limit) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_LIMIT_EXCEEDED,
                    "每人限领 " + limit + " 张，您已领取 " + owned + " 张");
        }
        if (!couponRepository.occupyIssuedQty(couponId)) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_LIMIT_EXCEEDED, "该优惠券已领完");
        }
        CouponUser coupon = CouponUser.issue(template, userId, owned + 1);
        try {
            couponUserRepository.insert(coupon);
        } catch (DuplicateKeyException exception) {
            // (coupon_id, user_id, claim_seq) 撞键 = 并发领同一张券，
            // 后到的这一笔整个事务回滚，连发行量占用一起退回去
            throw MallErrorCode.exception(MallErrorCode.COUPON_LIMIT_EXCEEDED, "领取过于频繁，请稍后重试");
        }
        return toUserView(coupon, null, null);
    }

    @Override
    public PageResult<CouponUserView> mine(String status, Long current, Long size, MallActor actor) {
        Long userId = actor.requireUserId();
        long pageSize = pageSize(size);
        long pageNo = pageNo(current);
        List<CouponUser> coupons = couponUserRepository.pageMine(userId, status,
                (pageNo - 1L) * pageSize, (int) pageSize);
        List<CouponUserView> views = new ArrayList<>(coupons.size());
        for (CouponUser coupon : coupons) {
            views.add(toUserView(coupon, null, null));
        }
        return PageResult.of(views, couponUserRepository.countMine(userId, status), pageNo, pageSize);
    }

    @Override
    public List<CouponUserView> available(Long channelId, BigDecimal goodsAmount, MallActor actor) {
        Long userId = actor.requireUserId();
        LocalDateTime now = LocalDateTime.now();
        BigDecimal goods = goodsAmount == null ? BigDecimal.ZERO : goodsAmount;
        List<CouponUser> candidates = couponUserRepository.listUsable(userId, now);
        List<CouponUserView> views = new ArrayList<>(candidates.size());
        for (CouponUser coupon : candidates) {
            CouponUsability usability = coupon.check(channelId, goods, now);
            BigDecimal discount = usability.usable() ? coupon.discountFor(goods) : null;
            views.add(toUserView(coupon, discount, usability));
        }
        // 可用的排前面，可用的按减得多排序：买家要的是「哪张最划算」。
        // 用布尔键而不是可用性枚举名：按名字排会把 THRESHOLD_NOT_MET 排到 USABLE 前面
        views.sort(Comparator
                .comparing((CouponUserView view) -> !CouponUsability.USABLE.name().equals(view.getUsability()))
                .thenComparing(CouponUserView::getDiscountAmount, Comparator.nullsLast(Comparator.reverseOrder())));
        return views;
    }

    @Override
    public CouponUser requireUsable(Long couponUserId, Long userId, Long channelId, BigDecimal goodsAmount) {
        CouponUser coupon = couponUserRepository.findById(couponUserId)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.COUPON_NOT_FOUND));
        if (!userId.equals(coupon.getUserId())) {
            // 用别人的券不解释成「不存在」，否则买家永远不知道是自己传错了 id
            throw MallErrorCode.exception(MallErrorCode.COUPON_NOT_FOUND, "该优惠券不属于当前用户");
        }
        CouponUsability usability = coupon.check(channelId, goodsAmount, LocalDateTime.now());
        switch (usability) {
            case USABLE -> {
                return coupon;
            }
            case USED -> throw MallErrorCode.exception(MallErrorCode.COUPON_STATE_INVALID, "该优惠券已被使用");
            case EXPIRED -> throw MallErrorCode.exception(MallErrorCode.COUPON_STATE_INVALID, "该优惠券已过期");
            case CHANNEL_MISMATCH -> throw MallErrorCode.exception(MallErrorCode.COUPON_CHANNEL_MISMATCH);
            default -> throw MallErrorCode.exception(MallErrorCode.COUPON_THRESHOLD_NOT_MET);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void use(Long couponUserId, String orderNo) {
        int affected = couponUserRepository.markUsed(couponUserId, orderNo, LocalDateTime.now());
        if (affected == 0) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_STATE_INVALID,
                    "该优惠券已被其他订单使用，请重新选择");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseByOrderNo(String orderNo) {
        return couponUserRepository.releaseByOrderNo(orderNo);
    }

    /**
     * 按主键取模板，不存在则抛业务异常。
     *
     * @param id 模板 ID
     * @return 模板
     */
    private Coupon requireTemplate(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> MallErrorCode.exception(MallErrorCode.COUPON_NOT_FOUND));
    }

    /**
     * 把命令字段写入模板。
     *
     * @param coupon  目标模板
     * @param command 保存命令
     */
    private void apply(Coupon coupon, CouponSaveCommand command) {
        coupon.setCouponCode(command.getCouponCode());
        coupon.setCouponName(command.getCouponName());
        CouponType type = CouponType.parse(command.getCouponType());
        if (type == null) {
            throw MallErrorCode.exception(MallErrorCode.COUPON_INVALID,
                    "未知券类型：" + command.getCouponType());
        }
        coupon.setCouponType(type);
        coupon.setThresholdAmount(command.getThresholdAmount());
        coupon.setFaceAmount(command.getFaceAmount());
        coupon.setDiscountRate(command.getDiscountRate());
        coupon.setMaxDiscountAmount(command.getMaxDiscountAmount());
        coupon.setChannelId(command.getChannelId());
        coupon.setTotalQty(command.getTotalQty() == null ? 0 : command.getTotalQty());
        coupon.setIssuedQty(coupon.getIssuedQty() == null ? 0 : coupon.getIssuedQty());
        coupon.setPerUserLimit(command.getPerUserLimit() == null ? 1 : command.getPerUserLimit());
        coupon.setValidFrom(command.getValidFrom());
        coupon.setValidTo(command.getValidTo());
        coupon.setStatus(CouponStatus.parse(command.getStatus()));
        coupon.setRemark(command.getRemark() == null ? null : command.getRemark().trim());
    }

    /**
     * 分页大小（带上限）。
     *
     * @param size 请求条数
     * @return 实际条数
     */
    private long pageSize(Long size) {
        return size == null || size < 1
                ? Math.min(20, properties.getPageLimit()) : Math.min(size, properties.getPageLimit());
    }

    /**
     * 页码（最小 1）。
     *
     * @param current 请求页码
     * @return 实际页码
     */
    private long pageNo(Long current) {
        return current == null || current < 1 ? 1L : current;
    }

    /**
     * 模板转视图。
     *
     * @param coupon 模板
     * @return 视图
     */
    private CouponView toView(Coupon coupon) {
        CouponView view = new CouponView();
        view.setId(coupon.getId());
        view.setCouponCode(coupon.getCouponCode());
        view.setCouponName(coupon.getCouponName());
        view.setCouponType(coupon.getCouponType() == null ? null : coupon.getCouponType().name());
        view.setThresholdAmount(coupon.getThresholdAmount());
        view.setFaceAmount(coupon.getFaceAmount());
        view.setDiscountRate(coupon.getDiscountRate());
        view.setMaxDiscountAmount(coupon.getMaxDiscountAmount());
        view.setChannelId(coupon.getChannelId());
        view.setTotalQty(coupon.getTotalQty());
        view.setIssuedQty(coupon.getIssuedQty());
        view.setPerUserLimit(coupon.getPerUserLimit());
        view.setValidFrom(coupon.getValidFrom());
        view.setValidTo(coupon.getValidTo());
        view.setStatus(coupon.getStatus() == null ? null : coupon.getStatus().name());
        view.setRemark(coupon.getRemark());
        view.setCreateTime(coupon.getCreateTime());
        view.setUpdateTime(coupon.getUpdateTime());
        return view;
    }

    /**
     * 用户券转视图。
     *
     * @param coupon     用户券
     * @param discount   本单可减金额（无订单上下文时为空）
     * @param usability  可用性（无订单上下文时为空）
     * @return 视图
     */
    private CouponUserView toUserView(CouponUser coupon, BigDecimal discount, CouponUsability usability) {
        CouponUserView view = new CouponUserView();
        view.setId(coupon.getId());
        view.setCouponId(coupon.getCouponId());
        view.setCouponCode(coupon.getCouponCode());
        view.setCouponName(coupon.getCouponName());
        view.setCouponType(coupon.getCouponType() == null ? null : coupon.getCouponType().name());
        view.setThresholdAmount(coupon.getThresholdAmount());
        view.setFaceAmount(coupon.getFaceAmount());
        view.setDiscountRate(coupon.getDiscountRate());
        view.setMaxDiscountAmount(coupon.getMaxDiscountAmount());
        view.setChannelId(coupon.getChannelId());
        view.setValidFrom(coupon.getValidFrom());
        view.setValidTo(coupon.getValidTo());
        view.setStatus(coupon.getStatus() == null ? null : coupon.getStatus().name());
        view.setOrderNo(coupon.getOrderNo());
        view.setUsedTime(coupon.getUsedTime());
        view.setDiscountAmount(discount);
        if (usability != null) {
            view.setUsability(usability.name());
            view.setUsabilityLabel(usability.label());
        }
        return view;
    }
}
