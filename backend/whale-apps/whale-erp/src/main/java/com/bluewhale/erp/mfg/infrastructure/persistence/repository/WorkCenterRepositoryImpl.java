package com.bluewhale.erp.mfg.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.WorkCenter;
import com.bluewhale.erp.mfg.domain.repository.WorkCenterRepository;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.WorkCenterEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.WorkCenterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 工作中心仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class WorkCenterRepositoryImpl implements WorkCenterRepository {

    private final WorkCenterMapper workCenterMapper;

    @Override
    public List<WorkCenter> page(MfgQuery query) {
        Page<WorkCenterEntity> page = workCenterMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MfgQuery query) {
        return workCenterMapper.selectCount(filter(query));
    }

    @Override
    public List<WorkCenter> listAll() {
        return workCenterMapper.selectList(new LambdaQueryWrapper<WorkCenterEntity>()
                        .orderByAsc(WorkCenterEntity::getCode))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<WorkCenter> findById(Long id) {
        return Optional.ofNullable(workCenterMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<WorkCenter> findByCode(String code) {
        return Optional.ofNullable(workCenterMapper.selectOne(new LambdaQueryWrapper<WorkCenterEntity>()
                .eq(WorkCenterEntity::getCode, code))).map(this::toDomain);
    }

    @Override
    public void insert(WorkCenter workCenter) {
        WorkCenterEntity entity = new WorkCenterEntity();
        fill(entity, workCenter);
        workCenterMapper.insert(entity);
        workCenter.setId(entity.getId());
    }

    @Override
    public void update(WorkCenter workCenter) {
        WorkCenterEntity entity = new WorkCenterEntity();
        entity.setId(workCenter.getId());
        fill(entity, workCenter);
        workCenterMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        workCenterMapper.deleteById(id);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<WorkCenterEntity> filter(MfgQuery query) {
        LambdaQueryWrapper<WorkCenterEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(WorkCenterEntity::getCode, query.getKeyword())
                    .or().like(WorkCenterEntity::getName, query.getKeyword())
                    .or().like(WorkCenterEntity::getDepartment, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(WorkCenterEntity::getStatus, query.getStatus());
        }
        return wrapper.orderByAsc(WorkCenterEntity::getCode);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 工作中心
     */
    private WorkCenter toDomain(WorkCenterEntity entity) {
        WorkCenter center = new WorkCenter();
        center.setId(entity.getId());
        center.setCode(entity.getCode());
        center.setName(entity.getName());
        center.setDepartment(entity.getDepartment());
        center.setDailyCapacityHours(entity.getDailyCapacityHours());
        center.setEfficiencyRate(entity.getEfficiencyRate());
        center.setWorkDays(entity.getWorkDays());
        center.setStatus(entity.getStatus());
        center.setRemark(entity.getRemark());
        return center;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param center 工作中心
     */
    private void fill(WorkCenterEntity entity, WorkCenter center) {
        entity.setCode(center.getCode());
        entity.setName(center.getName());
        entity.setDepartment(center.getDepartment());
        entity.setDailyCapacityHours(center.getDailyCapacityHours());
        entity.setEfficiencyRate(center.getEfficiencyRate());
        entity.setWorkDays(center.getWorkDays());
        entity.setStatus(center.getStatus());
        entity.setRemark(center.getRemark());
    }
}
