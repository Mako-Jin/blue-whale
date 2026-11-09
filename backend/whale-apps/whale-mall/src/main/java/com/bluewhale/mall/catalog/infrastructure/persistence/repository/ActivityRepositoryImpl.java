package com.bluewhale.mall.catalog.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.bluewhale.mall.catalog.domain.model.Activity;
import com.bluewhale.mall.catalog.domain.model.ActivityStatus;
import com.bluewhale.mall.catalog.domain.repository.ActivityRepository;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.ActivityEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 限时特价活动仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ActivityRepositoryImpl implements ActivityRepository {

    private final ActivityMapper activityMapper;

    @Override
    public List<Activity> page(String code, String name, Long skuId, Long channelId, String status,
                               long offset, int size) {
        return activityMapper.selectList(buildWrapper(code, name, skuId, channelId, status)
                        .last("LIMIT " + Math.max(offset, 0) + "," + Math.max(size, 1)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long count(String code, String name, Long skuId, Long channelId, String status) {
        return activityMapper.selectCount(buildWrapper(code, name, skuId, channelId, status));
    }

    @Override
    public Activity findById(Long id) {
        if (id == null) {
            return null;
        }
        ActivityEntity entity = activityMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public boolean existsCode(String activityCode, Long excludeId) {
        return activityMapper.selectCount(new LambdaQueryWrapper<ActivityEntity>()
                .eq(ActivityEntity::getActivityCode, activityCode)
                .ne(excludeId != null, ActivityEntity::getId, excludeId)) > 0;
    }

    @Override
    public List<Activity> matching(List<Long> skuIds, LocalDateTime now) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return activityMapper.selectList(new LambdaQueryWrapper<ActivityEntity>()
                        .eq(ActivityEntity::getStatus, ActivityStatus.ACTIVE.name())
                        .in(ActivityEntity::getSkuId, skuIds)
                        .le(ActivityEntity::getValidFrom, now)
                        .ge(ActivityEntity::getValidTo, now))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Long save(Activity activity) {
        ActivityEntity entity = toEntity(activity);
        if (entity.getId() == null) {
            activityMapper.insert(entity);
        } else {
            activityMapper.updateById(entity);
        }
        activity.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void updateStatus(Long id, String status) {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(id);
        entity.setStatus(status);
        activityMapper.updateById(entity);
    }

    /**
     * 组装查询条件。
     *
     * @param code      编码（模糊，可空）
     * @param name      名称（模糊，可空）
     * @param skuId     SKU ID（可空）
     * @param channelId 渠道 ID（可空）
     * @param status    状态（可空）
     * @return 查询条件
     */
    private LambdaQueryWrapper<ActivityEntity> buildWrapper(String code, String name, Long skuId,
                                                            Long channelId, String status) {
        LambdaQueryWrapper<ActivityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(code), ActivityEntity::getActivityCode, code)
                .like(StringUtils.isNotBlank(name), ActivityEntity::getActivityName, name)
                .eq(skuId != null, ActivityEntity::getSkuId, skuId)
                .eq(channelId != null, ActivityEntity::getChannelId, channelId)
                .eq(StringUtils.isNotBlank(status), ActivityEntity::getStatus, status)
                .orderByDesc(ActivityEntity::getId);
        return wrapper;
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Activity toDomain(ActivityEntity entity) {
        Activity activity = new Activity();
        activity.setId(entity.getId());
        activity.setActivityCode(entity.getActivityCode());
        activity.setActivityName(entity.getActivityName());
        activity.setChannelId(entity.getChannelId());
        activity.setSkuId(entity.getSkuId());
        activity.setActivityPrice(entity.getActivityPrice());
        activity.setValidFrom(entity.getValidFrom());
        activity.setValidTo(entity.getValidTo());
        activity.setStatus(ActivityStatus.parse(entity.getStatus()));
        activity.setRemark(entity.getRemark());
        activity.setCreateTime(entity.getCreateTime());
        activity.setUpdateTime(entity.getUpdateTime());
        return activity;
    }

    /**
     * 领域模型转实体。
     *
     * @param activity 领域模型
     * @return 实体
     */
    private ActivityEntity toEntity(Activity activity) {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(activity.getId());
        entity.setActivityCode(activity.getActivityCode());
        entity.setActivityName(activity.getActivityName());
        entity.setChannelId(activity.getChannelId());
        entity.setSkuId(activity.getSkuId());
        entity.setActivityPrice(activity.getActivityPrice());
        entity.setValidFrom(activity.getValidFrom());
        entity.setValidTo(activity.getValidTo());
        entity.setStatus(activity.getStatus() == null
                ? ActivityStatus.ACTIVE.name() : activity.getStatus().name());
        entity.setRemark(activity.getRemark());
        return entity;
    }
}
