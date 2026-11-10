package com.bluewhale.mall.catalog.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.mall.catalog.domain.model.StockInbound;
import com.bluewhale.mall.catalog.domain.repository.StockInboundRepository;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.StockInboundEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.StockInboundItemEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.StockInboundItemMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.StockInboundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 库存入库单仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class StockInboundRepositoryImpl implements StockInboundRepository {

    private final StockInboundMapper stockInboundMapper;
    private final StockInboundItemMapper stockInboundItemMapper;

    @Override
    public StockInbound findByDocNo(String docNo) {
        if (docNo == null || docNo.isBlank()) {
            return null;
        }
        StockInboundEntity entity = stockInboundMapper.selectOne(new LambdaQueryWrapper<StockInboundEntity>()
                .eq(StockInboundEntity::getDocNo, docNo.trim())
                .last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        List<StockInboundItemEntity> items = stockInboundItemMapper.selectList(
                new LambdaQueryWrapper<StockInboundItemEntity>()
                        .eq(StockInboundItemEntity::getInboundId, entity.getId()));
        StockInbound inbound = new StockInbound();
        inbound.setId(entity.getId());
        inbound.setDocNo(entity.getDocNo());
        inbound.setSource(entity.getSource());
        inbound.setRemark(entity.getRemark());
        inbound.setCreateTime(entity.getCreateTime());
        inbound.setLines(items.stream().map(this::toLineDomain).toList());
        return inbound;
    }

    @Override
    public Long save(StockInbound inbound) {
        StockInboundEntity entity = new StockInboundEntity();
        entity.setDocNo(inbound.getDocNo());
        entity.setSource(inbound.getSource());
        entity.setLineCount(inbound.getLines().size());
        entity.setRemark(inbound.getRemark());
        stockInboundMapper.insert(entity);
        for (StockInbound.StockInboundLine line : inbound.getLines()) {
            StockInboundItemEntity itemEntity = new StockInboundItemEntity();
            itemEntity.setInboundId(entity.getId());
            itemEntity.setSkuId(line.getSkuId());
            itemEntity.setSkuCode(line.getSkuCode());
            itemEntity.setQuantity(line.getQuantity());
            stockInboundItemMapper.insert(itemEntity);
            line.setId(itemEntity.getId());
            line.setInboundId(entity.getId());
        }
        inbound.setId(entity.getId());
        inbound.setCreateTime(entity.getCreateTime());
        return entity.getId();
    }

    /**
     * 明细实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private StockInbound.StockInboundLine toLineDomain(StockInboundItemEntity entity) {
        StockInbound.StockInboundLine line = new StockInbound.StockInboundLine();
        line.setId(entity.getId());
        line.setInboundId(entity.getInboundId());
        line.setSkuId(entity.getSkuId());
        line.setSkuCode(entity.getSkuCode());
        line.setQuantity(entity.getQuantity());
        return line;
    }
}
