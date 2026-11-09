package com.bluewhale.mall.coupon.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.mall.coupon.domain.model.CouponScope;
import com.bluewhale.mall.coupon.domain.model.CouponType;
import com.bluewhale.mall.coupon.domain.model.CouponUser;
import com.bluewhale.mall.coupon.domain.model.CouponUserStatus;
import com.bluewhale.mall.coupon.domain.repository.CouponUserRepository;
import com.bluewhale.mall.coupon.infrastructure.persistence.entity.CouponUserEntity;
import com.bluewhale.mall.coupon.infrastructure.persistence.mapper.CouponUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户券仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CouponUserRepositoryImpl implements CouponUserRepository {

    private final CouponUserMapper couponUserMapper;

    @Override
    public Long insert(CouponUser coupon) {
        CouponUserEntity entity = toEntity(coupon);
        couponUserMapper.insert(entity);
        coupon.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public Optional<CouponUser> findById(Long id) {
        return Optional.ofNullable(couponUserMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public int countByUser(Long userId, Long couponId) {
        return Math.toIntExact(couponUserMapper.selectCount(new LambdaQueryWrapper<CouponUserEntity>()
                .eq(CouponUserEntity::getUserId, userId)
                .eq(CouponUserEntity::getCouponId, couponId)));
    }

    @Override
    public List<CouponUser> pageMine(Long userId, String status, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<CouponUserEntity> page = couponUserMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), mineFilter(userId, status));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countMine(Long userId, String status) {
        return couponUserMapper.selectCount(mineFilter(userId, status));
    }

    @Override
    public List<CouponUser> listUsable(Long userId, LocalDateTime now) {
        return couponUserMapper.selectList(new LambdaQueryWrapper<CouponUserEntity>()
                        .eq(CouponUserEntity::getUserId, userId)
                        .eq(CouponUserEntity::getStatus, CouponUserStatus.UNUSED.name())
                        .ge(now != null, CouponUserEntity::getValidTo, now)
                        .le(now != null, CouponUserEntity::getValidFrom, now)
                        .orderByAsc(CouponUserEntity::getValidTo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public int markUsed(Long id, String orderNo, LocalDateTime now) {
        return couponUserMapper.markUsed(id, orderNo, now);
    }

    @Override
    public int release(Long id) {
        return couponUserMapper.release(id);
    }

    @Override
    public int releaseByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return 0;
        }
        return couponUserMapper.releaseByOrderNo(orderNo.trim());
    }

    /**
     * 我的券查询条件。
     *
     * @param userId 用户 ID
     * @param status 状态过滤
     * @return 查询包装器
     */
    private LambdaQueryWrapper<CouponUserEntity> mineFilter(Long userId, String status) {
        LambdaQueryWrapper<CouponUserEntity> wrapper = new LambdaQueryWrapper<CouponUserEntity>()
                .eq(CouponUserEntity::getUserId, userId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(CouponUserEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByAsc(CouponUserEntity::getStatus).orderByAsc(CouponUserEntity::getValidTo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private CouponUser toDomain(CouponUserEntity entity) {
        CouponUser coupon = new CouponUser();
        coupon.setId(entity.getId());
        coupon.setCouponId(entity.getCouponId());
        coupon.setUserId(entity.getUserId());
        coupon.setClaimSeq(entity.getClaimSeq());
        coupon.setCouponCode(entity.getCouponCode());
        coupon.setCouponName(entity.getCouponName());
        coupon.setCouponType(CouponType.parse(entity.getCouponType()));
        coupon.setThresholdAmount(entity.getThresholdAmount());
        coupon.setFaceAmount(entity.getFaceAmount());
        coupon.setDiscountRate(entity.getDiscountRate());
        coupon.setMaxDiscountAmount(entity.getMaxDiscountAmount());
        coupon.setChannelId(entity.getChannelId());
        coupon.setScope(CouponScope.parse(entity.getScope()));
        coupon.setCategoryIds(parseCategoryIds(entity.getCategoryIds()));
        coupon.setValidFrom(entity.getValidFrom());
        coupon.setValidTo(entity.getValidTo());
        coupon.setStatus(CouponUserStatus.parse(entity.getStatus()));
        coupon.setOrderNo(entity.getOrderNo());
        coupon.setUsedTime(entity.getUsedTime());
        coupon.setCreateTime(entity.getCreateTime());
        coupon.setUpdateTime(entity.getUpdateTime());
        return coupon;
    }

    /**
     * 领域模型转实体。
     *
     * @param coupon 领域模型
     * @return 实体
     */
    private CouponUserEntity toEntity(CouponUser coupon) {
        CouponUserEntity entity = new CouponUserEntity();
        entity.setId(coupon.getId());
        entity.setCouponId(coupon.getCouponId());
        entity.setUserId(coupon.getUserId());
        entity.setClaimSeq(coupon.getClaimSeq());
        entity.setCouponCode(coupon.getCouponCode());
        entity.setCouponName(coupon.getCouponName());
        entity.setCouponType(coupon.getCouponType() == null ? null : coupon.getCouponType().name());
        entity.setThresholdAmount(coupon.getThresholdAmount());
        entity.setFaceAmount(coupon.getFaceAmount());
        entity.setDiscountRate(coupon.getDiscountRate());
        entity.setMaxDiscountAmount(coupon.getMaxDiscountAmount());
        entity.setChannelId(coupon.getChannelId());
        entity.setScope(coupon.getScope() == null ? null : coupon.getScope().name());
        entity.setCategoryIds(joinCategoryIds(coupon.getCategoryIds()));
        entity.setValidFrom(coupon.getValidFrom());
        entity.setValidTo(coupon.getValidTo());
        entity.setStatus(coupon.getStatus() == null
                ? CouponUserStatus.UNUSED.name() : coupon.getStatus().name());
        entity.setOrderNo(coupon.getOrderNo());
        entity.setUsedTime(coupon.getUsedTime());
        return entity;
    }

    /**
     * 类目 ID 列表转逗号分隔文本（品类券快照落库用）。
     *
     * @param categoryIds 类目 ID 列表
     * @return 逗号分隔文本，空为 null
     */
    private String joinCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return null;
        }
        return categoryIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * 逗号分隔文本转类目 ID 列表。
     *
     * @param value 逗号分隔文本
     * @return 类目 ID 列表，空为 null
     */
    private List<Long> parseCategoryIds(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
