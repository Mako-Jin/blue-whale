package com.bluewhale.lms.training.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.lms.training.domain.model.ClassSession;
import com.bluewhale.lms.training.domain.model.TrainingClass;
import com.bluewhale.lms.training.domain.model.TrainingClassQuery;
import com.bluewhale.lms.training.domain.model.TrainingClassStatus;
import com.bluewhale.lms.training.domain.model.TrainingMode;
import com.bluewhale.lms.training.domain.repository.TrainingClassRepository;
import com.bluewhale.lms.training.infrastructure.persistence.entity.ClassSessionEntity;
import com.bluewhale.lms.training.infrastructure.persistence.entity.TrainingClassEntity;
import com.bluewhale.lms.training.infrastructure.persistence.mapper.ClassSessionMapper;
import com.bluewhale.lms.training.infrastructure.persistence.mapper.TrainingClassMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 培训班聚合仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TrainingClassRepositoryImpl implements TrainingClassRepository {

    private final TrainingClassMapper classMapper;

    private final ClassSessionMapper sessionMapper;

    @Override
    public Optional<TrainingClass> findById(Long id) {
        return Optional.ofNullable(classMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<TrainingClass> findByCode(String classCode) {
        return Optional.ofNullable(classMapper.selectOne(new LambdaQueryWrapper<TrainingClassEntity>()
                        .eq(TrainingClassEntity::getClassCode, classCode)))
                .map(this::toDomain);
    }

    @Override
    public Long save(TrainingClass trainingClass) {
        TrainingClassEntity entity = toEntity(trainingClass);
        if (trainingClass.getId() == null) {
            classMapper.insert(entity);
        } else {
            classMapper.updateById(entity);
        }
        trainingClass.setId(entity.getId());
        trainingClass.setCreateTime(entity.getCreateTime());
        trainingClass.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public void delete(Long id) {
        classMapper.deleteById(id);
    }

    @Override
    public List<TrainingClass> page(TrainingClassQuery query, long offset, int size) {
        int pageSize = Math.max(size, 1);
        long current = Math.max(offset, 0) / pageSize + 1;
        Page<TrainingClassEntity> page = classMapper.selectPage(new Page<>(current, pageSize), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(TrainingClassQuery query) {
        return classMapper.selectCount(filter(query));
    }

    @Override
    public List<ClassSession> listSessions(Long classId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ClassSessionEntity>()
                        .eq(ClassSessionEntity::getClassId, classId)
                        .orderByAsc(ClassSessionEntity::getSessionNo))
                .stream().map(this::toSessionDomain).toList();
    }

    @Override
    public Optional<ClassSession> findSessionById(Long sessionId) {
        return Optional.ofNullable(sessionMapper.selectById(sessionId)).map(this::toSessionDomain);
    }

    @Override
    public long countSessions(Long classId) {
        return sessionMapper.selectCount(new LambdaQueryWrapper<ClassSessionEntity>()
                .eq(ClassSessionEntity::getClassId, classId));
    }

    @Override
    public Long saveSession(ClassSession session) {
        ClassSessionEntity entity = toSessionEntity(session);
        if (session.getId() == null) {
            sessionMapper.insert(entity);
        } else {
            sessionMapper.updateById(entity);
        }
        session.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void deleteSession(Long sessionId) {
        sessionMapper.deleteById(sessionId);
    }

    /**
     * 组装培训班查询条件。
     *
     * @param query 查询条件
     * @return 包装器
     */
    private LambdaQueryWrapper<TrainingClassEntity> filter(TrainingClassQuery query) {
        LambdaQueryWrapper<TrainingClassEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(TrainingClassEntity::getTitle, keyword)
                    .or().like(TrainingClassEntity::getClassCode, keyword));
        }
        if (StringUtils.hasText(query.getMode())) {
            wrapper.eq(TrainingClassEntity::getMode, query.getMode().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(TrainingClassEntity::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(TrainingClassEntity::getStartDate);
        return wrapper;
    }

    /**
     * 实体转领域。
     *
     * @param entity 实体
     * @return 培训班
     */
    private TrainingClass toDomain(TrainingClassEntity entity) {
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setId(entity.getId());
        trainingClass.setClassCode(entity.getClassCode());
        trainingClass.setTitle(entity.getTitle());
        trainingClass.setCourseId(entity.getCourseId());
        trainingClass.setMode(TrainingMode.parse(entity.getMode()));
        trainingClass.setLecturer(entity.getLecturer());
        trainingClass.setStartDate(entity.getStartDate());
        trainingClass.setEndDate(entity.getEndDate());
        trainingClass.setLocation(entity.getLocation());
        trainingClass.setCapacity(entity.getCapacity());
        trainingClass.setStatus(TrainingClassStatus.valueOf(entity.getStatus()));
        trainingClass.setRemark(entity.getRemark());
        trainingClass.setCreateTime(entity.getCreateTime());
        trainingClass.setUpdateTime(entity.getUpdateTime());
        return trainingClass;
    }

    /**
     * 领域转实体。
     *
     * @param trainingClass 培训班
     * @return 实体
     */
    private TrainingClassEntity toEntity(TrainingClass trainingClass) {
        TrainingClassEntity entity = new TrainingClassEntity();
        entity.setId(trainingClass.getId());
        entity.setClassCode(trainingClass.getClassCode());
        entity.setTitle(trainingClass.getTitle());
        entity.setCourseId(trainingClass.getCourseId());
        entity.setMode(trainingClass.getMode().name());
        entity.setLecturer(trainingClass.getLecturer());
        entity.setStartDate(trainingClass.getStartDate());
        entity.setEndDate(trainingClass.getEndDate());
        entity.setLocation(trainingClass.getLocation());
        entity.setCapacity(trainingClass.getCapacity());
        entity.setStatus(trainingClass.getStatus().name());
        entity.setRemark(trainingClass.getRemark());
        entity.setCreateTime(trainingClass.getCreateTime());
        entity.setUpdateTime(trainingClass.getUpdateTime());
        return entity;
    }

    /**
     * 场次实体转领域。
     *
     * @param entity 实体
     * @return 场次
     */
    private ClassSession toSessionDomain(ClassSessionEntity entity) {
        ClassSession session = new ClassSession();
        session.setId(entity.getId());
        session.setClassId(entity.getClassId());
        session.setSessionNo(entity.getSessionNo());
        session.setTitle(entity.getTitle());
        session.setStartTime(entity.getStartTime());
        session.setEndTime(entity.getEndTime());
        session.setLocation(entity.getLocation());
        session.setLecturer(entity.getLecturer());
        session.setHours(entity.getHours());
        return session;
    }

    /**
     * 场次领域转实体。
     *
     * @param session 场次
     * @return 实体
     */
    private ClassSessionEntity toSessionEntity(ClassSession session) {
        ClassSessionEntity entity = new ClassSessionEntity();
        entity.setId(session.getId());
        entity.setClassId(session.getClassId());
        entity.setSessionNo(session.getSessionNo());
        entity.setTitle(session.getTitle());
        entity.setStartTime(session.getStartTime());
        entity.setEndTime(session.getEndTime());
        entity.setLocation(session.getLocation());
        entity.setLecturer(session.getLecturer());
        entity.setHours(session.getHours());
        return entity;
    }
}
