package com.bluewhale.lms.training.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.lms.training.domain.model.Checkin;
import com.bluewhale.lms.training.domain.model.CheckinType;
import com.bluewhale.lms.training.domain.repository.CheckinRepository;
import com.bluewhale.lms.training.infrastructure.persistence.entity.CheckinEntity;
import com.bluewhale.lms.training.infrastructure.persistence.mapper.CheckinMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 签到仓储实现（MyBatis-Plus）。
 *
 * <p>重复签到由表约束 {@code uk_lr_checkin}（tenant_id + session_id + user_id）
 * 与应用层幂等查询共同兜底。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CheckinRepositoryImpl implements CheckinRepository {

    private final CheckinMapper checkinMapper;

    @Override
    public Optional<Checkin> findBySessionAndUser(Long sessionId, Long userId) {
        return Optional.ofNullable(checkinMapper.selectOne(new LambdaQueryWrapper<CheckinEntity>()
                        .eq(CheckinEntity::getSessionId, sessionId)
                        .eq(CheckinEntity::getUserId, userId)))
                .map(this::toDomain);
    }

    @Override
    public Long save(Checkin checkin) {
        CheckinEntity entity = toEntity(checkin);
        checkinMapper.insert(entity);
        checkin.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public List<Checkin> listByClass(Long classId, Long sessionId) {
        LambdaQueryWrapper<CheckinEntity> wrapper = new LambdaQueryWrapper<CheckinEntity>()
                .eq(CheckinEntity::getClassId, classId)
                .orderByAsc(CheckinEntity::getCheckinAt);
        if (sessionId != null) {
            wrapper.eq(CheckinEntity::getSessionId, sessionId);
        }
        return checkinMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 签到记录
     */
    private Checkin toDomain(CheckinEntity entity) {
        Checkin checkin = new Checkin();
        checkin.setId(entity.getId());
        checkin.setClassId(entity.getClassId());
        checkin.setSessionId(entity.getSessionId());
        checkin.setUserId(entity.getUserId());
        checkin.setUsername(entity.getUsername());
        checkin.setCheckinType(CheckinType.valueOf(entity.getCheckinType()));
        checkin.setCheckinAt(entity.getCheckinAt());
        checkin.setOperatorId(entity.getOperatorId());
        checkin.setRemark(entity.getRemark());
        return checkin;
    }

    /**
     * 领域转实体。
     *
     * @param checkin 签到记录
     * @return 实体
     */
    private CheckinEntity toEntity(Checkin checkin) {
        CheckinEntity entity = new CheckinEntity();
        entity.setId(checkin.getId());
        entity.setClassId(checkin.getClassId());
        entity.setSessionId(checkin.getSessionId());
        entity.setUserId(checkin.getUserId());
        entity.setUsername(checkin.getUsername());
        entity.setCheckinType(checkin.getCheckinType().name());
        entity.setCheckinAt(checkin.getCheckinAt());
        entity.setOperatorId(checkin.getOperatorId());
        entity.setRemark(checkin.getRemark());
        return entity;
    }
}
