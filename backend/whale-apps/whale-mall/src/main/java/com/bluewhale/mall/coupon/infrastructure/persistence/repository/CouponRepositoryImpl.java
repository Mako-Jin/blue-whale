package com.bluewhale.mall.coupon.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.mall.coupon.domain.model.Coupon;
import com.bluewhale.mall.coupon.domain.model.CouponQuery;
import com.bluewhale.mall.coupon.domain.model.CouponStatus;
import com.bluewhale.mall.coupon.domain.model.CouponType;
import com.bluewhale.mall.coupon.domain.repository.CouponRepository;
import com.bluewhale.mall.coupon.infrastructure.persistence.entity.CouponEntity;
import com.bluewhale.mall.coupon.infrastructure.persistence.mapper.CouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 优惠券模板仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponMapper couponMapper;

    @Override
    public List<Coupon> page(CouponQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<CouponEntity> page = couponMapper.selectPage(new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(CouponQuery query) {
        return couponMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(couponMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsCode(String couponCode, Long excludeId) {
        if (!StringUtils.hasText(couponCode)) {
            return false;
        }
        return couponMapper.selectCount(new LambdaQueryWrapper<CouponEntity>()
                .eq(CouponEntity::getCouponCode, couponCode.trim().toUpperCase(Locale.ROOT))
                .ne(excludeId != null, CouponEntity::getId, excludeId)) > 0;
    }

    @Override
    public Long save(Coupon coupon) {
        CouponEntity entity = toEntity(coupon);
        if (entity.getId() == null) {
            couponMapper.insert(entity);
        } else {
            couponMapper.updateById(entity);
        }
        coupon.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public boolean occupyIssuedQty(Long id) {
        return couponMapper.occupyIssuedQty(id) > 0;
    }

    @Override
    public void updateStatus(Long id, String status) {
        CouponEntity entity = new CouponEntity();
        entity.setId(id);
        entity.setStatus(status);
        couponMapper.updateById(entity);
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<CouponEntity> filter(CouponQuery query) {
        LambdaQueryWrapper<CouponEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null && StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(CouponEntity::getCouponCode, keyword)
                    .or().like(CouponEntity::getCouponName, keyword));
        }
        if (query != null && StringUtils.hasText(query.getCouponType())) {
            wrapper.eq(CouponEntity::getCouponType, query.getCouponType().trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && StringUtils.hasText(query.getStatus())) {
            wrapper.eq(CouponEntity::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByDesc(CouponEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Coupon toDomain(CouponEntity entity) {
        Coupon coupon = new Coupon();
        coupon.setId(entity.getId());
        coupon.setCouponCode(entity.getCouponCode());
        coupon.setCouponName(entity.getCouponName());
        coupon.setCouponType(CouponType.parse(entity.getCouponType()));
        coupon.setThresholdAmount(entity.getThresholdAmount());
        coupon.setFaceAmount(entity.getFaceAmount());
        coupon.setDiscountRate(entity.getDiscountRate());
        coupon.setMaxDiscountAmount(entity.getMaxDiscountAmount());
        coupon.setChannelId(entity.getChannelId());
        coupon.setTotalQty(entity.getTotalQty());
        coupon.setIssuedQty(entity.getIssuedQty());
        coupon.setPerUserLimit(entity.getPerUserLimit());
        coupon.setValidFrom(entity.getValidFrom());
        coupon.setValidTo(entity.getValidTo());
        coupon.setStatus(CouponStatus.parse(entity.getStatus()));
        coupon.setRemark(entity.getRemark());
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
    private CouponEntity toEntity(Coupon coupon) {
        CouponEntity entity = new CouponEntity();
        entity.setId(coupon.getId());
        entity.setCouponCode(coupon.getCouponCode());
        entity.setCouponName(coupon.getCouponName());
        entity.setCouponType(coupon.getCouponType() == null ? null : coupon.getCouponType().name());
        entity.setThresholdAmount(coupon.getThresholdAmount());
        entity.setFaceAmount(coupon.getFaceAmount());
        entity.setDiscountRate(coupon.getDiscountRate());
        entity.setMaxDiscountAmount(coupon.getMaxDiscountAmount());
        entity.setChannelId(coupon.getChannelId());
        entity.setTotalQty(coupon.getTotalQty());
        entity.setIssuedQty(coupon.getIssuedQty());
        entity.setPerUserLimit(coupon.getPerUserLimit());
        entity.setValidFrom(coupon.getValidFrom());
        entity.setValidTo(coupon.getValidTo());
        entity.setStatus(coupon.getStatus() == null ? CouponStatus.ACTIVE.name() : coupon.getStatus().name());
        entity.setRemark(coupon.getRemark());
        return entity;
    }
}
