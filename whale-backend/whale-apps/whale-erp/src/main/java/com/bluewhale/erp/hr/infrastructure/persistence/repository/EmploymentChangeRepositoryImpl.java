package com.bluewhale.erp.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.hr.domain.model.EmploymentChange;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.repository.EmploymentChangeRepository;
import com.bluewhale.erp.hr.infrastructure.persistence.entity.EmploymentChangeEntity;
import com.bluewhale.erp.hr.infrastructure.persistence.mapper.EmploymentChangeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 员工异动仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class EmploymentChangeRepositoryImpl implements EmploymentChangeRepository {

    /** 单号前缀 */
    private static final String DOC_PREFIX = "HC-";

    /** 单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final EmploymentChangeMapper changeMapper;

    @Override
    public List<EmploymentChange> page(HrQuery query) {
        Page<EmploymentChangeEntity> page = changeMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(HrQuery query) {
        return changeMapper.selectCount(filter(query));
    }

    @Override
    public Optional<EmploymentChange> findById(Long id) {
        EmploymentChangeEntity entity = changeMapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public String nextChangeNo() {
        long sequence = changeMapper.selectCount(null) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, LocalDate.now().format(DOC_DATE_FORMATTER), sequence);
    }

    @Override
    public void insert(EmploymentChange change) {
        EmploymentChangeEntity entity = new EmploymentChangeEntity();
        fill(entity, change);
        changeMapper.insert(entity);
        change.setId(entity.getId());
    }

    @Override
    public void update(EmploymentChange change) {
        EmploymentChangeEntity entity = new EmploymentChangeEntity();
        entity.setId(change.getId());
        entity.setStatus(change.getStatus());
        changeMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<EmploymentChangeEntity> filter(HrQuery query) {
        LambdaQueryWrapper<EmploymentChangeEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(EmploymentChangeEntity::getChangeNo, query.getKeyword())
                    .or().like(EmploymentChangeEntity::getEmployeeNo, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(EmploymentChangeEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getEmployeeNo())) {
            wrapper.eq(EmploymentChangeEntity::getEmployeeNo, query.getEmployeeNo());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(EmploymentChangeEntity::getEffectiveDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(EmploymentChangeEntity::getEffectiveDate, query.getToDate());
        }
        return wrapper.orderByDesc(EmploymentChangeEntity::getEffectiveDate)
                .orderByDesc(EmploymentChangeEntity::getChangeNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private EmploymentChange toDomain(EmploymentChangeEntity entity) {
        EmploymentChange change = new EmploymentChange();
        change.setId(entity.getId());
        change.setChangeNo(entity.getChangeNo());
        change.setEmployeeNo(entity.getEmployeeNo());
        change.setChangeType(entity.getChangeType());
        change.setEffectiveDate(entity.getEffectiveDate());
        change.setFromOrgCode(entity.getFromOrgCode());
        change.setToOrgCode(entity.getToOrgCode());
        change.setFromPosition(entity.getFromPosition());
        change.setToPosition(entity.getToPosition());
        change.setFromSalary(entity.getFromSalary());
        change.setToSalary(entity.getToSalary());
        change.setStatus(entity.getStatus());
        change.setRemark(entity.getRemark());
        return change;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param change 领域模型
     */
    private void fill(EmploymentChangeEntity entity, EmploymentChange change) {
        entity.setChangeNo(change.getChangeNo());
        entity.setEmployeeNo(change.getEmployeeNo());
        entity.setChangeType(change.getChangeType());
        entity.setEffectiveDate(change.getEffectiveDate());
        entity.setFromOrgCode(change.getFromOrgCode());
        entity.setToOrgCode(change.getToOrgCode());
        entity.setFromPosition(change.getFromPosition());
        entity.setToPosition(change.getToPosition());
        entity.setFromSalary(change.getFromSalary());
        entity.setToSalary(change.getToSalary());
        entity.setStatus(change.getStatus());
        entity.setRemark(change.getRemark());
    }
}
