package com.bluewhale.erp.mfg.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.MpsPlan;
import com.bluewhale.erp.mfg.domain.model.MpsPlanLine;
import com.bluewhale.erp.mfg.domain.repository.MpsRepository;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.MpsEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.MpsLineEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.MpsLineMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.MpsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 主生产计划仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MpsRepositoryImpl implements MpsRepository {

    /** 计划单号前缀 */
    private static final String DOC_PREFIX = "MP-";

    private final MpsMapper mpsMapper;

    private final MpsLineMapper mpsLineMapper;

    @Override
    public List<MpsPlan> page(MfgQuery query) {
        Page<MpsEntity> page = mpsMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MfgQuery query) {
        return mpsMapper.selectCount(filter(query));
    }

    @Override
    public Optional<MpsPlan> findById(Long id) {
        MpsEntity entity = mpsMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        MpsPlan plan = toDomain(entity);
        plan.setLines(mpsLineMapper.selectList(new LambdaQueryWrapper<MpsLineEntity>()
                        .eq(MpsLineEntity::getMpsId, id)
                        .orderByAsc(MpsLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(plan);
    }

    @Override
    public String nextPlanNo(String period) {
        long sequence = mpsMapper.selectCount(new LambdaQueryWrapper<MpsEntity>()
                .eq(MpsEntity::getPeriod, period)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, period.replace("-", ""), sequence);
    }

    @Override
    public void insert(MpsPlan plan) {
        MpsEntity entity = new MpsEntity();
        fill(entity, plan);
        mpsMapper.insert(entity);
        plan.setId(entity.getId());
        for (MpsPlanLine line : plan.getLines()) {
            MpsLineEntity lineEntity = new MpsLineEntity();
            lineEntity.setMpsId(entity.getId());
            fillLine(lineEntity, line);
            mpsLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(MpsPlan plan) {
        MpsEntity entity = new MpsEntity();
        entity.setId(plan.getId());
        entity.setStatus(plan.getStatus());
        entity.setTotalPlanned(plan.getTotalPlanned());
        mpsMapper.updateById(entity);
        for (MpsPlanLine line : plan.getLines()) {
            MpsLineEntity lineEntity = mpsLineMapper.selectOne(new LambdaQueryWrapper<MpsLineEntity>()
                    .eq(MpsLineEntity::getMpsId, plan.getId())
                    .eq(MpsLineEntity::getLineNo, line.getLineNo())
                    .last("limit 1"));
            if (lineEntity != null) {
                lineEntity.setReleasedQuantity(line.getReleasedQuantity());
                mpsLineMapper.updateById(lineEntity);
            }
        }
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<MpsEntity> filter(MfgQuery query) {
        LambdaQueryWrapper<MpsEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MpsEntity::getPlanNo, query.getKeyword())
                    .or().like(MpsEntity::getPeriod, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(MpsEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getPeriod())) {
            wrapper.eq(MpsEntity::getPeriod, query.getPeriod());
        }
        return wrapper.orderByDesc(MpsEntity::getPeriod).orderByDesc(MpsEntity::getPlanNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MpsPlan toDomain(MpsEntity entity) {
        MpsPlan plan = new MpsPlan();
        plan.setId(entity.getId());
        plan.setPlanNo(entity.getPlanNo());
        plan.setPeriod(entity.getPeriod());
        plan.setStatus(entity.getStatus());
        plan.setTotalPlanned(entity.getTotalPlanned());
        plan.setRemark(entity.getRemark());
        return plan;
    }

    /**
     * 分录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MpsPlanLine toDomain(MpsLineEntity entity) {
        MpsPlanLine line = new MpsPlanLine();
        line.setLineNo(entity.getLineNo());
        line.setItemId(entity.getItemId());
        line.setItemCode(entity.getItemCode());
        line.setItemName(entity.getItemName());
        line.setUom(entity.getUom());
        line.setPlannedQuantity(entity.getPlannedQuantity());
        line.setReleasedQuantity(entity.getReleasedQuantity());
        return line;
    }

    /**
     * 领域模型写入单头实体。
     *
     * @param entity 实体
     * @param plan 领域模型
     */
    private void fill(MpsEntity entity, MpsPlan plan) {
        entity.setPlanNo(plan.getPlanNo());
        entity.setPeriod(plan.getPeriod());
        entity.setStatus(plan.getStatus());
        entity.setTotalPlanned(plan.getTotalPlanned());
        entity.setRemark(plan.getRemark());
    }

    /**
     * 领域模型写入分录实体。
     *
     * @param entity 实体
     * @param line 领域模型
     */
    private void fillLine(MpsLineEntity entity, MpsPlanLine line) {
        entity.setLineNo(line.getLineNo());
        entity.setItemId(line.getItemId());
        entity.setItemCode(line.getItemCode());
        entity.setItemName(line.getItemName());
        entity.setUom(line.getUom());
        entity.setPlannedQuantity(line.getPlannedQuantity());
        entity.setReleasedQuantity(line.getReleasedQuantity());
    }
}
