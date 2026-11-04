package com.bluewhale.erp.masterdata.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;
import com.bluewhale.erp.masterdata.domain.repository.WarehouseRepository;
import com.bluewhale.erp.masterdata.infrastructure.persistence.entity.WarehouseEntity;
import com.bluewhale.erp.masterdata.infrastructure.persistence.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 仓库仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class WarehouseRepositoryImpl implements WarehouseRepository {

    private final WarehouseMapper warehouseMapper;

    @Override
    public List<Warehouse> page(MasterDataQuery query) {
        Page<WarehouseEntity> page = warehouseMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MasterDataQuery query) {
        return warehouseMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Warehouse> findById(Long id) {
        return Optional.ofNullable(warehouseMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Warehouse> findByCode(String code) {
        return Optional.ofNullable(warehouseMapper.selectOne(new LambdaQueryWrapper<WarehouseEntity>()
                .eq(WarehouseEntity::getCode, code))).map(this::toDomain);
    }

    @Override
    public void insert(Warehouse warehouse) {
        WarehouseEntity entity = new WarehouseEntity();
        fill(entity, warehouse);
        warehouseMapper.insert(entity);
        warehouse.setId(entity.getId());
    }

    @Override
    public void update(Warehouse warehouse) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(warehouse.getId());
        fill(entity, warehouse);
        warehouseMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<WarehouseEntity> filter(MasterDataQuery query) {
        LambdaQueryWrapper<WarehouseEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(WarehouseEntity::getCode, query.getKeyword())
                    .or().like(WarehouseEntity::getName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(WarehouseEntity::getStatus, query.getStatus());
        }
        return wrapper.orderByAsc(WarehouseEntity::getCode);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Warehouse toDomain(WarehouseEntity entity) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(entity.getId());
        warehouse.setCode(entity.getCode());
        warehouse.setName(entity.getName());
        warehouse.setManager(entity.getManager());
        warehouse.setAddress(entity.getAddress());
        warehouse.setStatus(entity.getStatus());
        warehouse.setRemark(entity.getRemark());
        return warehouse;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param warehouse 领域模型
     */
    private void fill(WarehouseEntity entity, Warehouse warehouse) {
        entity.setCode(warehouse.getCode());
        entity.setName(warehouse.getName());
        entity.setManager(warehouse.getManager());
        entity.setAddress(warehouse.getAddress());
        entity.setStatus(warehouse.getStatus());
        entity.setRemark(warehouse.getRemark());
    }
}
