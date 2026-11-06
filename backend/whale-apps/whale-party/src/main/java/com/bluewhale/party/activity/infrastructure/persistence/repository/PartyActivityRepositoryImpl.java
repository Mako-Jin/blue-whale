package com.bluewhale.party.activity.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.activity.domain.model.ActivitySignin;
import com.bluewhale.party.activity.domain.model.ActivityStatus;
import com.bluewhale.party.activity.domain.model.ActivityType;
import com.bluewhale.party.activity.domain.model.PartyActivity;
import com.bluewhale.party.activity.domain.model.PartyActivityQuery;
import com.bluewhale.party.activity.domain.repository.PartyActivityRepository;
import com.bluewhale.party.activity.infrastructure.persistence.entity.ActivitySigninEntity;
import com.bluewhale.party.activity.infrastructure.persistence.entity.PartyActivityEntity;
import com.bluewhale.party.activity.infrastructure.persistence.mapper.ActivitySigninMapper;
import com.bluewhale.party.activity.infrastructure.persistence.mapper.PartyActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 组织生活仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PartyActivityRepositoryImpl implements PartyActivityRepository {

    /** 到会标记值（TINYINT 1） */
    private static final int PRESENT = 1;

    private final PartyActivityMapper partyActivityMapper;

    private final ActivitySigninMapper activitySigninMapper;

    @Override
    public List<PartyActivity> page(PartyActivityQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<PartyActivityEntity> page = partyActivityMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(PartyActivityQuery query) {
        return partyActivityMapper.selectCount(filter(query));
    }

    @Override
    public Optional<PartyActivity> findById(Long id) {
        return Optional.ofNullable(partyActivityMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Long save(PartyActivity activity) {
        PartyActivityEntity entity = toEntity(activity);
        if (entity.getId() == null) {
            partyActivityMapper.insert(entity);
        } else {
            partyActivityMapper.updateById(entity);
        }
        activity.setId(entity.getId());
        activity.setCreateTime(entity.getCreateTime());
        activity.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public List<ActivitySignin> listSignins(Long activityId) {
        return activitySigninMapper.selectList(new LambdaQueryWrapper<ActivitySigninEntity>()
                        .eq(ActivitySigninEntity::getActivityId, activityId)
                        .orderByAsc(ActivitySigninEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Long saveSignin(ActivitySignin signin) {
        ActivitySigninEntity existing = activitySigninMapper.selectOne(
                new LambdaQueryWrapper<ActivitySigninEntity>()
                        .eq(ActivitySigninEntity::getActivityId, signin.getActivityId())
                        .eq(ActivitySigninEntity::getMemberId, signin.getMemberId()));
        ActivitySigninEntity entity = toEntity(signin);
        if (existing == null) {
            activitySigninMapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            activitySigninMapper.updateById(entity);
        }
        signin.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public long countPresent(Long activityId) {
        return activitySigninMapper.selectCount(new LambdaQueryWrapper<ActivitySigninEntity>()
                .eq(ActivitySigninEntity::getActivityId, activityId)
                .eq(ActivitySigninEntity::getPresent, PRESENT));
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<PartyActivityEntity> filter(PartyActivityQuery query) {
        LambdaQueryWrapper<PartyActivityEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PartyActivityEntity::getPlannedDate);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(PartyActivityEntity::getTitle, query.getKeyword().trim());
        }
        if (query.getBranchId() != null) {
            wrapper.eq(PartyActivityEntity::getBranchId, query.getBranchId());
        }
        if (StringUtils.hasText(query.getActivityType())) {
            wrapper.eq(PartyActivityEntity::getActivityType,
                    query.getActivityType().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PartyActivityEntity::getStatus,
                    query.getStatus().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (query.getFrom() != null) {
            wrapper.ge(PartyActivityEntity::getPlannedDate, query.getFrom());
        }
        if (query.getTo() != null) {
            wrapper.le(PartyActivityEntity::getPlannedDate, query.getTo());
        }
        return wrapper.orderByDesc(PartyActivityEntity::getPlannedDate)
                .orderByDesc(PartyActivityEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PartyActivity toDomain(PartyActivityEntity entity) {
        PartyActivity activity = new PartyActivity();
        activity.setId(entity.getId());
        activity.setBranchId(entity.getBranchId());
        activity.setActivityType(ActivityType.parse(entity.getActivityType()));
        activity.setTitle(entity.getTitle());
        activity.setPlannedDate(entity.getPlannedDate());
        activity.setHeldDate(entity.getHeldDate());
        activity.setLocation(entity.getLocation());
        activity.setHostName(entity.getHostName());
        activity.setExpectedCount(entity.getExpectedCount());
        activity.setStatus(ActivityStatus.parse(entity.getStatus()));
        activity.setSummary(entity.getSummary());
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
    private PartyActivityEntity toEntity(PartyActivity activity) {
        PartyActivityEntity entity = new PartyActivityEntity();
        entity.setId(activity.getId());
        entity.setBranchId(activity.getBranchId());
        entity.setActivityType(activity.getActivityType() == null
                ? ActivityType.MEMBER_ASSEMBLY.name() : activity.getActivityType().name());
        entity.setTitle(activity.getTitle());
        entity.setPlannedDate(activity.getPlannedDate());
        entity.setHeldDate(activity.getHeldDate());
        entity.setLocation(activity.getLocation());
        entity.setHostName(activity.getHostName());
        entity.setExpectedCount(activity.getExpectedCount());
        entity.setStatus(activity.getStatus() == null
                ? ActivityStatus.PLANNED.name() : activity.getStatus().name());
        entity.setSummary(activity.getSummary());
        entity.setRemark(activity.getRemark());
        return entity;
    }

    /**
     * 签到实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ActivitySignin toDomain(ActivitySigninEntity entity) {
        ActivitySignin signin = new ActivitySignin();
        signin.setId(entity.getId());
        signin.setActivityId(entity.getActivityId());
        signin.setMemberId(entity.getMemberId());
        signin.setMemberName(entity.getMemberName());
        signin.setPresent(entity.getPresent() != null && entity.getPresent() == PRESENT);
        signin.setAbsentReason(entity.getAbsentReason());
        signin.setSignedAt(entity.getSignedAt());
        return signin;
    }

    /**
     * 签到领域模型转实体。
     *
     * @param signin 领域模型
     * @return 实体
     */
    private ActivitySigninEntity toEntity(ActivitySignin signin) {
        ActivitySigninEntity entity = new ActivitySigninEntity();
        entity.setId(signin.getId());
        entity.setActivityId(signin.getActivityId());
        entity.setMemberId(signin.getMemberId());
        entity.setMemberName(signin.getMemberName());
        entity.setPresent(signin.isPresent() ? PRESENT : 0);
        entity.setAbsentReason(signin.getAbsentReason());
        entity.setSignedAt(signin.getSignedAt());
        return entity;
    }
}
