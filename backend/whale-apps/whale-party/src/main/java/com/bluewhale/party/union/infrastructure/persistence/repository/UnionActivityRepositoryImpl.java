package com.bluewhale.party.union.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.union.domain.model.SignupStatus;
import com.bluewhale.party.union.domain.model.UnionActivity;
import com.bluewhale.party.union.domain.model.UnionActivityQuery;
import com.bluewhale.party.union.domain.model.UnionActivityStatus;
import com.bluewhale.party.union.domain.model.UnionSignup;
import com.bluewhale.party.union.domain.repository.UnionActivityRepository;
import com.bluewhale.party.union.infrastructure.persistence.entity.UnionActivityEntity;
import com.bluewhale.party.union.infrastructure.persistence.entity.UnionSignupEntity;
import com.bluewhale.party.union.infrastructure.persistence.mapper.UnionActivityMapper;
import com.bluewhale.party.union.infrastructure.persistence.mapper.UnionSignupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 工会活动仓储实现（MyBatis-Plus）。
 *
 * <p>园地列表在 SQL 层就限定 {@code PUBLISHED} 并按开始时间升序：
 * 已结束的活动仍可被管理员在管理端看到，但不进入园地列表。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UnionActivityRepositoryImpl implements UnionActivityRepository {

    private final UnionActivityMapper unionActivityMapper;

    private final UnionSignupMapper unionSignupMapper;

    @Override
    public List<UnionActivity> page(UnionActivityQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<UnionActivityEntity> page = unionActivityMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query, false));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(UnionActivityQuery query) {
        return unionActivityMapper.selectCount(filter(query, false));
    }

    @Override
    public List<UnionActivity> portalPage(UnionActivityQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<UnionActivityEntity> page = unionActivityMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query, true));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long portalCount(UnionActivityQuery query) {
        return unionActivityMapper.selectCount(filter(query, true));
    }

    @Override
    public Optional<UnionActivity> findById(Long id) {
        return Optional.ofNullable(unionActivityMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<UnionActivity> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return unionActivityMapper.selectList(new LambdaQueryWrapper<UnionActivityEntity>()
                        .in(UnionActivityEntity::getId, ids))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Long save(UnionActivity activity) {
        UnionActivityEntity entity = toEntity(activity);
        if (entity.getId() == null) {
            unionActivityMapper.insert(entity);
        } else {
            unionActivityMapper.updateById(entity);
        }
        activity.setId(entity.getId());
        activity.setCreateTime(entity.getCreateTime());
        activity.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public Optional<UnionSignup> findSignup(Long activityId, Long unionMemberId) {
        if (activityId == null || unionMemberId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(unionSignupMapper.selectOne(new LambdaQueryWrapper<UnionSignupEntity>()
                        .eq(UnionSignupEntity::getActivityId, activityId)
                        .eq(UnionSignupEntity::getUnionMemberId, unionMemberId)))
                .map(this::toDomain);
    }

    @Override
    public long countSigned(Long activityId) {
        if (activityId == null) {
            return 0L;
        }
        return unionSignupMapper.selectCount(new LambdaQueryWrapper<UnionSignupEntity>()
                .eq(UnionSignupEntity::getActivityId, activityId)
                .eq(UnionSignupEntity::getStatus, SignupStatus.SIGNED.name()));
    }

    @Override
    public Long saveSignup(UnionSignup signup) {
        UnionSignupEntity entity = toEntity(signup);
        if (entity.getId() == null) {
            unionSignupMapper.insert(entity);
        } else {
            unionSignupMapper.updateById(entity);
        }
        signup.setId(entity.getId());
        signup.setCreateTime(entity.getCreateTime());
        signup.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public List<UnionSignup> pageSignups(Long activityId, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<UnionSignupEntity> page = unionSignupMapper.selectPage(
                new Page<>(current, Math.max(size, 1)),
                new LambdaQueryWrapper<UnionSignupEntity>()
                        .eq(UnionSignupEntity::getActivityId, activityId)
                        .orderByDesc(UnionSignupEntity::getId));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countSignups(Long activityId) {
        if (activityId == null) {
            return 0L;
        }
        return unionSignupMapper.selectCount(new LambdaQueryWrapper<UnionSignupEntity>()
                .eq(UnionSignupEntity::getActivityId, activityId));
    }

    @Override
    public List<UnionSignup> pageMySignups(Long userId, long offset, int size) {
        if (userId == null) {
            return List.of();
        }
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<UnionSignupEntity> page = unionSignupMapper.selectPage(
                new Page<>(current, Math.max(size, 1)),
                new LambdaQueryWrapper<UnionSignupEntity>()
                        .eq(UnionSignupEntity::getUserId, userId)
                        .orderByDesc(UnionSignupEntity::getId));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countMySignups(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return unionSignupMapper.selectCount(new LambdaQueryWrapper<UnionSignupEntity>()
                .eq(UnionSignupEntity::getUserId, userId));
    }

    @Override
    public List<UnionActivity> listByStartBetween(LocalDateTime from, LocalDateTime to) {
        return unionActivityMapper.selectList(new LambdaQueryWrapper<UnionActivityEntity>()
                        .ge(UnionActivityEntity::getStartTime, from)
                        .lt(UnionActivityEntity::getStartTime, to)
                        .orderByAsc(UnionActivityEntity::getStartTime))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<UnionSignup> listSignupsByActivityIds(Collection<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return List.of();
        }
        return unionSignupMapper.selectList(new LambdaQueryWrapper<UnionSignupEntity>()
                        .in(UnionSignupEntity::getActivityId, activityIds))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 组装活动查询条件。
     *
     * @param query 查询条件
     * @param portal 是否园地口径（仅已发布、按开始时间升序）
     * @return 查询包装器
     */
    private LambdaQueryWrapper<UnionActivityEntity> filter(UnionActivityQuery query, boolean portal) {
        LambdaQueryWrapper<UnionActivityEntity> wrapper = new LambdaQueryWrapper<>();
        if (portal) {
            wrapper.eq(UnionActivityEntity::getStatus, UnionActivityStatus.PUBLISHED.name());
        } else if (query != null && StringUtils.hasText(query.getStatus())) {
            wrapper.eq(UnionActivityEntity::getStatus,
                    query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (query != null && StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(UnionActivityEntity::getActivityNo, keyword)
                    .or().like(UnionActivityEntity::getTitle, keyword));
        }
        if (portal) {
            return wrapper.orderByAsc(UnionActivityEntity::getStartTime)
                    .orderByAsc(UnionActivityEntity::getId);
        }
        return wrapper.orderByDesc(UnionActivityEntity::getId);
    }

    /**
     * 活动实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private UnionActivity toDomain(UnionActivityEntity entity) {
        UnionActivity activity = new UnionActivity();
        activity.setId(entity.getId());
        activity.setActivityNo(entity.getActivityNo());
        activity.setTitle(entity.getTitle());
        activity.setActivityType(entity.getActivityType());
        activity.setStartTime(entity.getStartTime());
        activity.setEndTime(entity.getEndTime());
        activity.setLocation(entity.getLocation());
        activity.setQuota(entity.getQuota());
        activity.setStatus(UnionActivityStatus.parse(entity.getStatus()));
        activity.setRemark(entity.getRemark());
        activity.setCreateTime(entity.getCreateTime());
        activity.setUpdateTime(entity.getUpdateTime());
        return activity;
    }

    /**
     * 活动领域模型转实体。
     *
     * @param activity 领域模型
     * @return 实体
     */
    private UnionActivityEntity toEntity(UnionActivity activity) {
        UnionActivityEntity entity = new UnionActivityEntity();
        entity.setId(activity.getId());
        entity.setActivityNo(activity.getActivityNo());
        entity.setTitle(activity.getTitle());
        entity.setActivityType(activity.getActivityType());
        entity.setStartTime(activity.getStartTime());
        entity.setEndTime(activity.getEndTime());
        entity.setLocation(activity.getLocation());
        entity.setQuota(activity.getQuota());
        entity.setStatus(activity.getStatus() == null
                ? UnionActivityStatus.DRAFT.name() : activity.getStatus().name());
        entity.setRemark(activity.getRemark());
        return entity;
    }

    /**
     * 报名实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private UnionSignup toDomain(UnionSignupEntity entity) {
        UnionSignup signup = new UnionSignup();
        signup.setId(entity.getId());
        signup.setActivityId(entity.getActivityId());
        signup.setUnionMemberId(entity.getUnionMemberId());
        signup.setUserId(entity.getUserId());
        signup.setStatus(SignupStatus.parse(entity.getStatus()));
        signup.setSignupAt(entity.getSignupAt());
        signup.setCancelledAt(entity.getCancelledAt());
        signup.setCreateTime(entity.getCreateTime());
        signup.setUpdateTime(entity.getUpdateTime());
        return signup;
    }

    /**
     * 报名领域模型转实体。
     *
     * @param signup 领域模型
     * @return 实体
     */
    private UnionSignupEntity toEntity(UnionSignup signup) {
        UnionSignupEntity entity = new UnionSignupEntity();
        entity.setId(signup.getId());
        entity.setActivityId(signup.getActivityId());
        entity.setUnionMemberId(signup.getUnionMemberId());
        entity.setUserId(signup.getUserId());
        entity.setStatus(signup.getStatus() == null
                ? SignupStatus.SIGNED.name() : signup.getStatus().name());
        entity.setSignupAt(signup.getSignupAt());
        entity.setCancelledAt(signup.getCancelledAt());
        return entity;
    }
}
