package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.Stocktake;
import com.bluewhale.erp.scm.domain.model.StocktakeLine;
import com.bluewhale.erp.scm.domain.repository.StocktakeRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.StocktakeEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.StocktakeLineEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.StocktakeLineMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.StocktakeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 库存盘点单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class StocktakeRepositoryImpl implements StocktakeRepository {

    /** 盘点单号前缀 */
    private static final String DOC_PREFIX = "ST-";

    /** 单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StocktakeMapper stocktakeMapper;

    private final StocktakeLineMapper stocktakeLineMapper;

    @Override
    public List<Stocktake> page(ScmQuery query) {
        Page<StocktakeEntity> page = stocktakeMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ScmQuery query) {
        return stocktakeMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Stocktake> findById(Long id) {
        StocktakeEntity entity = stocktakeMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        Stocktake stocktake = toDomain(entity);
        stocktake.setLines(stocktakeLineMapper.selectList(new LambdaQueryWrapper<StocktakeLineEntity>()
                        .eq(StocktakeLineEntity::getStocktakeId, id)
                        .orderByAsc(StocktakeLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(stocktake);
    }

    @Override
    public String nextDocNo(LocalDate docDate) {
        long sequence = stocktakeMapper.selectCount(new LambdaQueryWrapper<StocktakeEntity>()
                .eq(StocktakeEntity::getStocktakeDate, docDate)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, docDate.format(DOC_DATE_FORMATTER), sequence);
    }

    @Override
    public void insert(Stocktake stocktake) {
        StocktakeEntity entity = new StocktakeEntity();
        fill(entity, stocktake);
        stocktakeMapper.insert(entity);
        stocktake.setId(entity.getId());
        for (StocktakeLine line : stocktake.getLines()) {
            StocktakeLineEntity lineEntity = new StocktakeLineEntity();
            lineEntity.setStocktakeId(entity.getId());
            fillLine(lineEntity, line);
            stocktakeLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(Stocktake stocktake) {
        StocktakeEntity entity = new StocktakeEntity();
        entity.setId(stocktake.getId());
        entity.setStatus(stocktake.getStatus());
        entity.setGainAmount(stocktake.getGainAmount());
        entity.setLossAmount(stocktake.getLossAmount());
        stocktakeMapper.updateById(entity);
        for (StocktakeLine line : stocktake.getLines()) {
            StocktakeLineEntity lineEntity = stocktakeLineMapper.selectOne(
                    new LambdaQueryWrapper<StocktakeLineEntity>()
                            .eq(StocktakeLineEntity::getStocktakeId, stocktake.getId())
                            .eq(StocktakeLineEntity::getLineNo, line.getLineNo())
                            .last("limit 1"));
            if (lineEntity != null) {
                lineEntity.setBookQuantity(line.getBookQuantity());
                lineEntity.setCountedQuantity(line.getCountedQuantity());
                lineEntity.setDifference(line.getDifference());
                lineEntity.setUnitCost(line.getUnitCost());
                lineEntity.setAmount(line.getAmount());
                stocktakeLineMapper.updateById(lineEntity);
            }
        }
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<StocktakeEntity> filter(ScmQuery query) {
        LambdaQueryWrapper<StocktakeEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(StocktakeEntity::getDocNo, query.getKeyword())
                    .or().like(StocktakeEntity::getWarehouseName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(StocktakeEntity::getStatus, query.getStatus());
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(StocktakeEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(StocktakeEntity::getStocktakeDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(StocktakeEntity::getStocktakeDate, query.getToDate());
        }
        return wrapper.orderByDesc(StocktakeEntity::getStocktakeDate)
                .orderByDesc(StocktakeEntity::getDocNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Stocktake toDomain(StocktakeEntity entity) {
        Stocktake stocktake = new Stocktake();
        stocktake.setId(entity.getId());
        stocktake.setDocNo(entity.getDocNo());
        stocktake.setStocktakeDate(entity.getStocktakeDate());
        stocktake.setWarehouseId(entity.getWarehouseId());
        stocktake.setWarehouseName(entity.getWarehouseName());
        stocktake.setStatus(entity.getStatus());
        stocktake.setGainAmount(entity.getGainAmount());
        stocktake.setLossAmount(entity.getLossAmount());
        stocktake.setRemark(entity.getRemark());
        return stocktake;
    }

    /**
     * 分录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private StocktakeLine toDomain(StocktakeLineEntity entity) {
        StocktakeLine line = new StocktakeLine();
        line.setLineNo(entity.getLineNo());
        line.setItemId(entity.getItemId());
        line.setItemCode(entity.getItemCode());
        line.setItemName(entity.getItemName());
        line.setUom(entity.getUom());
        line.setBookQuantity(entity.getBookQuantity());
        line.setCountedQuantity(entity.getCountedQuantity());
        line.setDifference(entity.getDifference());
        line.setUnitCost(entity.getUnitCost());
        line.setAmount(entity.getAmount());
        return line;
    }

    /**
     * 领域模型写入单头实体。
     *
     * @param entity 实体
     * @param stocktake 领域模型
     */
    private void fill(StocktakeEntity entity, Stocktake stocktake) {
        entity.setDocNo(stocktake.getDocNo());
        entity.setStocktakeDate(stocktake.getStocktakeDate());
        entity.setWarehouseId(stocktake.getWarehouseId());
        entity.setWarehouseName(stocktake.getWarehouseName());
        entity.setStatus(stocktake.getStatus());
        entity.setGainAmount(stocktake.getGainAmount());
        entity.setLossAmount(stocktake.getLossAmount());
        entity.setRemark(stocktake.getRemark());
    }

    /**
     * 领域模型写入分录实体。
     *
     * @param entity 实体
     * @param line 领域模型
     */
    private void fillLine(StocktakeLineEntity entity, StocktakeLine line) {
        entity.setLineNo(line.getLineNo());
        entity.setItemId(line.getItemId());
        entity.setItemCode(line.getItemCode());
        entity.setItemName(line.getItemName());
        entity.setUom(line.getUom());
        entity.setBookQuantity(line.getBookQuantity());
        entity.setCountedQuantity(line.getCountedQuantity());
        entity.setDifference(line.getDifference());
        entity.setUnitCost(line.getUnitCost());
        entity.setAmount(line.getAmount());
    }
}
