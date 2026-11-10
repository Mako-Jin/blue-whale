package com.bluewhale.erp.qc.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.qc.domain.model.OeeRecord;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.repository.OeeRepository;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.OeeRecordEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.OeeRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 设备 OEE 记录仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class OeeRepositoryImpl implements OeeRepository {

    private final OeeRecordMapper oeeRecordMapper;

    @Override
    public List<OeeRecord> page(QcQuery query) {
        Page<OeeRecordEntity> page = oeeRecordMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(QcQuery query) {
        return oeeRecordMapper.selectCount(filter(query));
    }

    @Override
    public Optional<OeeRecord> findByEquipmentAndDate(String equipmentNo, LocalDate recordDate) {
        OeeRecordEntity entity = oeeRecordMapper.selectOne(new LambdaQueryWrapper<OeeRecordEntity>()
                .eq(OeeRecordEntity::getEquipmentNo, equipmentNo)
                .eq(OeeRecordEntity::getRecordDate, recordDate)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public void insert(OeeRecord record) {
        OeeRecordEntity entity = new OeeRecordEntity();
        fill(entity, record);
        oeeRecordMapper.insert(entity);
        record.setId(entity.getId());
    }

    @Override
    public void update(OeeRecord record) {
        OeeRecordEntity entity = new OeeRecordEntity();
        entity.setId(record.getId());
        entity.setPlannedMinutes(record.getPlannedMinutes());
        entity.setRunMinutes(record.getRunMinutes());
        entity.setDownMinutes(record.getDownMinutes());
        entity.setIdealQty(record.getIdealQty());
        entity.setOutputQty(record.getOutputQty());
        entity.setGoodQty(record.getGoodQty());
        entity.setAvailabilityRate(record.getAvailabilityRate());
        entity.setPerformanceRate(record.getPerformanceRate());
        entity.setQualityRate(record.getQualityRate());
        entity.setOeeRate(record.getOeeRate());
        oeeRecordMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<OeeRecordEntity> filter(QcQuery query) {
        LambdaQueryWrapper<OeeRecordEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(OeeRecordEntity::getEquipmentNo, query.getKeyword());
        }
        if (StringUtils.hasText(query.getEquipmentNo())) {
            wrapper.eq(OeeRecordEntity::getEquipmentNo, query.getEquipmentNo());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(OeeRecordEntity::getRecordDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(OeeRecordEntity::getRecordDate, query.getToDate());
        }
        return wrapper.orderByDesc(OeeRecordEntity::getRecordDate)
                .orderByAsc(OeeRecordEntity::getEquipmentNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private OeeRecord toDomain(OeeRecordEntity entity) {
        OeeRecord record = new OeeRecord();
        record.setId(entity.getId());
        record.setEquipmentNo(entity.getEquipmentNo());
        record.setRecordDate(entity.getRecordDate());
        record.setPlannedMinutes(entity.getPlannedMinutes());
        record.setRunMinutes(entity.getRunMinutes());
        record.setDownMinutes(entity.getDownMinutes());
        record.setIdealQty(entity.getIdealQty());
        record.setOutputQty(entity.getOutputQty());
        record.setGoodQty(entity.getGoodQty());
        record.setAvailabilityRate(entity.getAvailabilityRate());
        record.setPerformanceRate(entity.getPerformanceRate());
        record.setQualityRate(entity.getQualityRate());
        record.setOeeRate(entity.getOeeRate());
        return record;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param record OEE 记录
     */
    private void fill(OeeRecordEntity entity, OeeRecord record) {
        entity.setEquipmentNo(record.getEquipmentNo());
        entity.setRecordDate(record.getRecordDate());
        entity.setPlannedMinutes(record.getPlannedMinutes());
        entity.setRunMinutes(record.getRunMinutes());
        entity.setDownMinutes(record.getDownMinutes());
        entity.setIdealQty(record.getIdealQty());
        entity.setOutputQty(record.getOutputQty());
        entity.setGoodQty(record.getGoodQty());
        entity.setAvailabilityRate(record.getAvailabilityRate());
        entity.setPerformanceRate(record.getPerformanceRate());
        entity.setQualityRate(record.getQualityRate());
        entity.setOeeRate(record.getOeeRate());
    }
}
