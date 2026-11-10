package com.bluewhale.erp.qc.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.qc.domain.model.Equipment;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.repository.EquipmentRepository;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.EquipmentEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.EquipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 设备档案仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class EquipmentRepositoryImpl implements EquipmentRepository {

    private final EquipmentMapper equipmentMapper;

    @Override
    public List<Equipment> page(QcQuery query) {
        Page<EquipmentEntity> page = equipmentMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(QcQuery query) {
        return equipmentMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Equipment> findByEquipmentNo(String equipmentNo) {
        EquipmentEntity entity = equipmentMapper.selectOne(new LambdaQueryWrapper<EquipmentEntity>()
                .eq(EquipmentEntity::getEquipmentNo, equipmentNo)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public void insert(Equipment equipment) {
        EquipmentEntity entity = new EquipmentEntity();
        fill(entity, equipment);
        equipmentMapper.insert(entity);
        equipment.setId(entity.getId());
    }

    @Override
    public void update(Equipment equipment) {
        EquipmentEntity entity = new EquipmentEntity();
        entity.setId(equipment.getId());
        entity.setName(equipment.getName());
        entity.setEquipmentType(equipment.getEquipmentType());
        entity.setLocation(equipment.getLocation());
        entity.setStatus(equipment.getStatus());
        entity.setPurchaseDate(equipment.getPurchaseDate());
        entity.setRemark(equipment.getRemark());
        equipmentMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<EquipmentEntity> filter(QcQuery query) {
        LambdaQueryWrapper<EquipmentEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(EquipmentEntity::getEquipmentNo, query.getKeyword())
                    .or().like(EquipmentEntity::getName, query.getKeyword())
                    .or().like(EquipmentEntity::getEquipmentType, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(EquipmentEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getEquipmentNo())) {
            wrapper.eq(EquipmentEntity::getEquipmentNo, query.getEquipmentNo());
        }
        return wrapper.orderByAsc(EquipmentEntity::getEquipmentNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Equipment toDomain(EquipmentEntity entity) {
        Equipment equipment = new Equipment();
        equipment.setId(entity.getId());
        equipment.setEquipmentNo(entity.getEquipmentNo());
        equipment.setName(entity.getName());
        equipment.setEquipmentType(entity.getEquipmentType());
        equipment.setLocation(entity.getLocation());
        equipment.setStatus(entity.getStatus());
        equipment.setPurchaseDate(entity.getPurchaseDate());
        equipment.setRemark(entity.getRemark());
        return equipment;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param equipment 设备
     */
    private void fill(EquipmentEntity entity, Equipment equipment) {
        entity.setEquipmentNo(equipment.getEquipmentNo());
        entity.setName(equipment.getName());
        entity.setEquipmentType(equipment.getEquipmentType());
        entity.setLocation(equipment.getLocation());
        entity.setStatus(equipment.getStatus());
        entity.setPurchaseDate(equipment.getPurchaseDate());
        entity.setRemark(equipment.getRemark());
    }
}
