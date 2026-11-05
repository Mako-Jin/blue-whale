package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.PurchaseOrder;
import com.bluewhale.erp.scm.domain.model.PurchaseOrderLine;
import com.bluewhale.erp.scm.domain.model.PurchasePriceRecord;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.repository.PurchaseOrderRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PurchaseOrderEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PurchaseOrderLineEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.PurchaseOrderLineMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.PurchaseOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采购订单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PurchaseOrderRepositoryImpl implements PurchaseOrderRepository {

    /** 订单号前缀 */
    private static final String DOC_PREFIX = "PO-";

    /** 订单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PurchaseOrderMapper purchaseOrderMapper;

    private final PurchaseOrderLineMapper purchaseOrderLineMapper;

    @Override
    public List<PurchaseOrder> page(ScmQuery query) {
        Page<PurchaseOrderEntity> page = purchaseOrderMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ScmQuery query) {
        return purchaseOrderMapper.selectCount(filter(query));
    }

    @Override
    public Optional<PurchaseOrder> findById(Long id) {
        PurchaseOrderEntity entity = purchaseOrderMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        PurchaseOrder order = toDomain(entity);
        order.setLines(purchaseOrderLineMapper.selectList(new LambdaQueryWrapper<PurchaseOrderLineEntity>()
                        .eq(PurchaseOrderLineEntity::getOrderId, id)
                        .orderByAsc(PurchaseOrderLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(order);
    }

    @Override
    public List<PurchasePriceRecord> listPurchasePrices(Long itemId, LocalDate fromDate) {
        List<PurchaseOrderLineEntity> lines = purchaseOrderLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderLineEntity>()
                        .eq(PurchaseOrderLineEntity::getItemId, itemId)
                        .orderByDesc(PurchaseOrderLineEntity::getId));
        if (lines.isEmpty()) {
            return List.of();
        }
        Set<Long> orderIds = lines.stream().map(PurchaseOrderLineEntity::getOrderId).collect(Collectors.toSet());
        Map<Long, PurchaseOrderEntity> headers = purchaseOrderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(PurchaseOrderEntity::getId, Function.identity()));
        List<PurchasePriceRecord> records = new ArrayList<>();
        for (PurchaseOrderLineEntity line : lines) {
            PurchaseOrderEntity header = headers.get(line.getOrderId());
            if (header == null || (fromDate != null && header.getDocDate().isBefore(fromDate))) {
                continue;
            }
            PurchasePriceRecord record = new PurchasePriceRecord();
            record.setSupplierId(header.getSupplierId());
            record.setSupplierName(header.getSupplierName());
            record.setDocDate(header.getDocDate());
            record.setStatus(header.getStatus());
            record.setItemId(line.getItemId());
            record.setItemCode(line.getItemCode());
            record.setItemName(line.getItemName());
            record.setUnitPrice(line.getUnitPrice());
            record.setQuantity(line.getQuantity());
            record.setCurrency(header.getCurrency());
            records.add(record);
        }
        return records;
    }

    @Override
    public String nextDocNo(LocalDate docDate) {
        long sequence = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrderEntity>()
                .eq(PurchaseOrderEntity::getDocDate, docDate)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, docDate.format(DOC_DATE_FORMATTER), sequence);
    }

    @Override
    public void insert(PurchaseOrder order) {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        fill(entity, order);
        purchaseOrderMapper.insert(entity);
        order.setId(entity.getId());
        for (PurchaseOrderLine line : order.getLines()) {
            PurchaseOrderLineEntity lineEntity = new PurchaseOrderLineEntity();
            lineEntity.setOrderId(entity.getId());
            fillLine(lineEntity, line);
            purchaseOrderLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(PurchaseOrder order) {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(order.getId());
        entity.setStatus(order.getStatus());
        entity.setReceivedAmount(order.getReceivedAmount());
        purchaseOrderMapper.updateById(entity);
        for (PurchaseOrderLine line : order.getLines()) {
            PurchaseOrderLineEntity lineEntity = purchaseOrderLineMapper.selectOne(
                    new LambdaQueryWrapper<PurchaseOrderLineEntity>()
                            .eq(PurchaseOrderLineEntity::getOrderId, order.getId())
                            .eq(PurchaseOrderLineEntity::getLineNo, line.getLineNo())
                            .last("limit 1"));
            if (lineEntity != null) {
                lineEntity.setReceivedQuantity(line.getReceivedQuantity());
                purchaseOrderLineMapper.updateById(lineEntity);
            }
        }
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<PurchaseOrderEntity> filter(ScmQuery query) {
        LambdaQueryWrapper<PurchaseOrderEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(PurchaseOrderEntity::getDocNo, query.getKeyword())
                    .or().like(PurchaseOrderEntity::getSupplierName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PurchaseOrderEntity::getStatus, query.getStatus());
        }
        if (query.getPartyId() != null) {
            wrapper.eq(PurchaseOrderEntity::getSupplierId, query.getPartyId());
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(PurchaseOrderEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(PurchaseOrderEntity::getDocDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(PurchaseOrderEntity::getDocDate, query.getToDate());
        }
        return wrapper.orderByDesc(PurchaseOrderEntity::getDocDate)
                .orderByDesc(PurchaseOrderEntity::getDocNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PurchaseOrder toDomain(PurchaseOrderEntity entity) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(entity.getId());
        order.setDocNo(entity.getDocNo());
        order.setSupplierId(entity.getSupplierId());
        order.setSupplierName(entity.getSupplierName());
        order.setDocDate(entity.getDocDate());
        order.setExpectedDate(entity.getExpectedDate());
        order.setWarehouseId(entity.getWarehouseId());
        order.setStatus(entity.getStatus());
        order.setCurrency(entity.getCurrency());
        order.setTotalAmount(entity.getTotalAmount());
        order.setReceivedAmount(entity.getReceivedAmount());
        order.setRemark(entity.getRemark());
        return order;
    }

    /**
     * 分录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PurchaseOrderLine toDomain(PurchaseOrderLineEntity entity) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setLineNo(entity.getLineNo());
        line.setItemId(entity.getItemId());
        line.setItemCode(entity.getItemCode());
        line.setItemName(entity.getItemName());
        line.setUom(entity.getUom());
        line.setQuantity(entity.getQuantity());
        line.setReceivedQuantity(entity.getReceivedQuantity());
        line.setUnitPrice(entity.getUnitPrice());
        line.setAmount(entity.getAmount());
        return line;
    }

    /**
     * 领域模型写入订单头实体。
     *
     * @param entity 实体
     * @param order 领域模型
     */
    private void fill(PurchaseOrderEntity entity, PurchaseOrder order) {
        entity.setDocNo(order.getDocNo());
        entity.setSupplierId(order.getSupplierId());
        entity.setSupplierName(order.getSupplierName());
        entity.setDocDate(order.getDocDate());
        entity.setExpectedDate(order.getExpectedDate());
        entity.setWarehouseId(order.getWarehouseId());
        entity.setStatus(order.getStatus());
        entity.setCurrency(order.getCurrency());
        entity.setTotalAmount(order.getTotalAmount());
        entity.setReceivedAmount(order.getReceivedAmount());
        entity.setRemark(order.getRemark());
    }

    /**
     * 领域模型写入分录实体。
     *
     * @param entity 实体
     * @param line 领域模型
     */
    private void fillLine(PurchaseOrderLineEntity entity, PurchaseOrderLine line) {
        entity.setLineNo(line.getLineNo());
        entity.setItemId(line.getItemId());
        entity.setItemCode(line.getItemCode());
        entity.setItemName(line.getItemName());
        entity.setUom(line.getUom());
        entity.setQuantity(line.getQuantity());
        entity.setReceivedQuantity(line.getReceivedQuantity());
        entity.setUnitPrice(line.getUnitPrice());
        entity.setAmount(line.getAmount());
    }
}
