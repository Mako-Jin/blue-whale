package com.bluewhale.lms.training.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.lms.training.domain.model.LecturerHours;
import com.bluewhale.lms.training.domain.repository.LecturerHoursRepository;
import com.bluewhale.lms.training.infrastructure.persistence.entity.LecturerHoursEntity;
import com.bluewhale.lms.training.infrastructure.persistence.mapper.LecturerHoursMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 讲师课时台账仓储实现（MyBatis-Plus）。
 *
 * <p>场次改讲师或改课时时走「先逻辑删除该场次旧台账、再写入新台账」，
 * 保证台账合计恒等于场次课时之和。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class LecturerHoursRepositoryImpl implements LecturerHoursRepository {

    private final LecturerHoursMapper hoursMapper;

    @Override
    public void replaceBySession(Long sessionId, List<LecturerHours> entries) {
        deleteBySession(sessionId);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (LecturerHours entry : entries) {
            hoursMapper.insert(toEntity(entry));
        }
    }

    @Override
    public void deleteBySession(Long sessionId) {
        hoursMapper.delete(new LambdaQueryWrapper<LecturerHoursEntity>()
                .eq(LecturerHoursEntity::getSessionId, sessionId));
    }

    @Override
    public List<LecturerHours> listByClass(Long classId) {
        return hoursMapper.selectList(new LambdaQueryWrapper<LecturerHoursEntity>()
                        .eq(LecturerHoursEntity::getClassId, classId))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 台账记录
     */
    private LecturerHours toDomain(LecturerHoursEntity entity) {
        LecturerHours hours = new LecturerHours();
        hours.setId(entity.getId());
        hours.setClassId(entity.getClassId());
        hours.setSessionId(entity.getSessionId());
        hours.setLecturer(entity.getLecturer());
        hours.setHours(entity.getHours());
        hours.setAccumulatedAt(entity.getAccumulatedAt());
        return hours;
    }

    /**
     * 领域转实体。
     *
     * @param hours 台账记录
     * @return 实体
     */
    private LecturerHoursEntity toEntity(LecturerHours hours) {
        LecturerHoursEntity entity = new LecturerHoursEntity();
        entity.setId(hours.getId());
        entity.setClassId(hours.getClassId());
        entity.setSessionId(hours.getSessionId());
        entity.setLecturer(hours.getLecturer());
        entity.setHours(hours.getHours());
        entity.setAccumulatedAt(hours.getAccumulatedAt());
        return entity;
    }
}
