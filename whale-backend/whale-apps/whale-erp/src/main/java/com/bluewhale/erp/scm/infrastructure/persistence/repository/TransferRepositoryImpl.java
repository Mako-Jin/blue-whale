package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.Transfer;
import com.bluewhale.erp.scm.domain.model.TransferLine;
import com.bluewhale.erp.scm.domain.repository.TransferRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.TransferEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.TransferLineEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.TransferLineMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.TransferMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 库存调拨单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TransferRepositoryImpl implements TransferRepository {

    /** 调拨单号前缀 */
    private static final String DOC_PREFIX = "TR-";

    /** 单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TransferMapper transferMapper;

    private final TransferLineMapper transferLineMapper;

    @Override
    public List<Transfer> page(ScmQuery query) {
        Page<TransferEntity> page = transferMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ScmQuery query) {
        return transferMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Transfer> findById(Long id) {
        TransferEntity entity = transferMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        Transfer transfer = toDomain(entity);
        transfer.setLines(transferLineMapper.selectList(new LambdaQueryWrapper<TransferLineEntity>()
                        .eq(TransferLineEntity::getTransferId, id)
                        .orderByAsc(TransferLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(transfer);
    }

    @Override
    public String nextDocNo(LocalDate docDate) {
        long sequence = transferMapper.selectCount(new LambdaQueryWrapper<TransferEntity>()
                .eq(TransferEntity::getTransferDate, docDate)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, docDate.format(DOC_DATE_FORMATTER), sequence);
    }

    @Override
    public void insert(Transfer transfer) {
        TransferEntity entity = new TransferEntity();
        fill(entity, transfer);
        transferMapper.insert(entity);
        transfer.setId(entity.getId());
        for (TransferLine line : transfer.getLines()) {
            TransferLineEntity lineEntity = new TransferLineEntity();
            lineEntity.setTransferId(entity.getId());
            fillLine(lineEntity, line);
            transferLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(Transfer transfer) {
        TransferEntity entity = new TransferEntity();
        entity.setId(transfer.getId());
        entity.setStatus(transfer.getStatus());
        entity.setTotalQuantity(transfer.getTotalQuantity());
        entity.setTotalAmount(transfer.getTotalAmount());
        transferMapper.updateById(entity);
        for (TransferLine line : transfer.getLines()) {
            TransferLineEntity lineEntity = transferLineMapper.selectOne(
                    new LambdaQueryWrapper<TransferLineEntity>()
                            .eq(TransferLineEntity::getTransferId, transfer.getId())
                            .eq(TransferLineEntity::getLineNo, line.getLineNo())
                            .last("limit 1"));
            if (lineEntity != null) {
                lineEntity.setUnitCost(line.getUnitCost());
                lineEntity.setAmount(line.getAmount());
                transferLineMapper.updateById(lineEntity);
            }
        }
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<TransferEntity> filter(ScmQuery query) {
        LambdaQueryWrapper<TransferEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(TransferEntity::getDocNo, query.getKeyword())
                    .or().like(TransferEntity::getFromWarehouseName, query.getKeyword())
                    .or().like(TransferEntity::getToWarehouseName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(TransferEntity::getStatus, query.getStatus());
        }
        if (query.getWarehouseId() != null) {
            wrapper.and(w -> w.eq(TransferEntity::getFromWarehouseId, query.getWarehouseId())
                    .or().eq(TransferEntity::getToWarehouseId, query.getWarehouseId()));
        }
        if (query.getFromDate() != null) {
            wrapper.ge(TransferEntity::getTransferDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(TransferEntity::getTransferDate, query.getToDate());
        }
        return wrapper.orderByDesc(TransferEntity::getTransferDate)
                .orderByDesc(TransferEntity::getDocNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Transfer toDomain(TransferEntity entity) {
        Transfer transfer = new Transfer();
        transfer.setId(entity.getId());
        transfer.setDocNo(entity.getDocNo());
        transfer.setTransferDate(entity.getTransferDate());
        transfer.setFromWarehouseId(entity.getFromWarehouseId());
        transfer.setFromWarehouseName(entity.getFromWarehouseName());
        transfer.setToWarehouseId(entity.getToWarehouseId());
        transfer.setToWarehouseName(entity.getToWarehouseName());
        transfer.setStatus(entity.getStatus());
        transfer.setTotalQuantity(entity.getTotalQuantity());
        transfer.setTotalAmount(entity.getTotalAmount());
        transfer.setRemark(entity.getRemark());
        return transfer;
    }

    /**
     * 分录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private TransferLine toDomain(TransferLineEntity entity) {
        TransferLine line = new TransferLine();
        line.setLineNo(entity.getLineNo());
        line.setItemId(entity.getItemId());
        line.setItemCode(entity.getItemCode());
        line.setItemName(entity.getItemName());
        line.setUom(entity.getUom());
        line.setQuantity(entity.getQuantity());
        line.setUnitCost(entity.getUnitCost());
        line.setAmount(entity.getAmount());
        return line;
    }

    /**
     * 领域模型写入单头实体。
     *
     * @param entity 实体
     * @param transfer 领域模型
     */
    private void fill(TransferEntity entity, Transfer transfer) {
        entity.setDocNo(transfer.getDocNo());
        entity.setTransferDate(transfer.getTransferDate());
        entity.setFromWarehouseId(transfer.getFromWarehouseId());
        entity.setFromWarehouseName(transfer.getFromWarehouseName());
        entity.setToWarehouseId(transfer.getToWarehouseId());
        entity.setToWarehouseName(transfer.getToWarehouseName());
        entity.setStatus(transfer.getStatus());
        entity.setTotalQuantity(transfer.getTotalQuantity());
        entity.setTotalAmount(transfer.getTotalAmount());
        entity.setRemark(transfer.getRemark());
    }

    /**
     * 领域模型写入分录实体。
     *
     * @param entity 实体
     * @param line 领域模型
     */
    private void fillLine(TransferLineEntity entity, TransferLine line) {
        entity.setLineNo(line.getLineNo());
        entity.setItemId(line.getItemId());
        entity.setItemCode(line.getItemCode());
        entity.setItemName(line.getItemName());
        entity.setUom(line.getUom());
        entity.setQuantity(line.getQuantity());
        entity.setUnitCost(line.getUnitCost());
        entity.setAmount(line.getAmount());
    }
}
