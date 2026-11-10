package com.bluewhale.erp.qc.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.model.QualityCost;
import com.bluewhale.erp.qc.domain.repository.QualityCostRepository;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.QualityCostEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.QualityCostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 质量成本仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class QualityCostRepositoryImpl implements QualityCostRepository {

    /** 单号前缀 */
    private static final String DOC_PREFIX = "QCOST-";

    /** 单号期间格式 */
    private static final DateTimeFormatter DOC_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final QualityCostMapper qualityCostMapper;

    @Override
    public List<QualityCost> page(QcQuery query) {
        Page<QualityCostEntity> page = qualityCostMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(QcQuery query) {
        return qualityCostMapper.selectCount(filter(query));
    }

    @Override
    public String nextCostNo(LocalDate costDate) {
        String period = costDate.format(DOC_PERIOD_FORMATTER);
        long sequence = qualityCostMapper.selectCount(new LambdaQueryWrapper<QualityCostEntity>()
                .likeRight(QualityCostEntity::getCostNo, DOC_PREFIX + period)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, period, sequence);
    }

    @Override
    public void insert(QualityCost cost) {
        QualityCostEntity entity = new QualityCostEntity();
        entity.setCostNo(cost.getCostNo());
        entity.setSourceType(cost.getSourceType());
        entity.setSourceNo(cost.getSourceNo());
        entity.setCostType(cost.getCostType());
        entity.setAmount(cost.getAmount());
        entity.setCostDate(cost.getCostDate());
        entity.setRemark(cost.getRemark());
        qualityCostMapper.insert(entity);
        cost.setId(entity.getId());
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<QualityCostEntity> filter(QcQuery query) {
        LambdaQueryWrapper<QualityCostEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(QualityCostEntity::getCostNo, query.getKeyword())
                    .or().like(QualityCostEntity::getSourceNo, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(QualityCostEntity::getSourceType, query.getSourceType());
        }
        if (StringUtils.hasText(query.getCostType())) {
            wrapper.eq(QualityCostEntity::getCostType, query.getCostType());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(QualityCostEntity::getCostDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(QualityCostEntity::getCostDate, query.getToDate());
        }
        return wrapper.orderByDesc(QualityCostEntity::getCostDate)
                .orderByDesc(QualityCostEntity::getCostNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private QualityCost toDomain(QualityCostEntity entity) {
        QualityCost cost = new QualityCost();
        cost.setId(entity.getId());
        cost.setCostNo(entity.getCostNo());
        cost.setSourceType(entity.getSourceType());
        cost.setSourceNo(entity.getSourceNo());
        cost.setCostType(entity.getCostType());
        cost.setAmount(entity.getAmount());
        cost.setCostDate(entity.getCostDate());
        cost.setRemark(entity.getRemark());
        return cost;
    }
}
