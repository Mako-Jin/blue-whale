package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.SalesOrder;
import com.bluewhale.erp.scm.domain.model.SalesOrderLine;
import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.ScmStatus;
import com.bluewhale.erp.scm.domain.repository.SalesOrderRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SalesOrderEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.SalesOrderLineEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.SalesOrderLineMapper;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 销售订单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SalesOrderRepositoryImpl implements SalesOrderRepository {

    /** 订单号前缀 */
    private static final String DOC_PREFIX = "SO-";

    /** 订单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SalesOrderMapper salesOrderMapper;

    private final SalesOrderLineMapper salesOrderLineMapper;

    @Override
    public List<SalesOrder> page(ScmQuery query) {
        Page<SalesOrderEntity> page = salesOrderMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ScmQuery query) {
        return salesOrderMapper.selectCount(filter(query));
    }

    @Override
    public Optional<SalesOrder> findById(Long id) {
        return findOne(new LambdaQueryWrapper<SalesOrderEntity>().eq(SalesOrderEntity::getId, id));
    }

    @Override
    public Optional<SalesOrder> findByDocNo(String docNo) {
        return findOne(new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getDocNo, docNo)
                .last("limit 1"));
    }

    @Override
    public Optional<SalesOrder> findByCrmOrderNo(String crmOrderNo) {
        return findOne(new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getCrmOrderNo, crmOrderNo)
                .last("limit 1"));
    }

    /**
     * 按条件查询订单头并装载分录。
     *
     * @param wrapper 查询条件
     * @return 销售订单
     */
    private Optional<SalesOrder> findOne(LambdaQueryWrapper<SalesOrderEntity> wrapper) {
        SalesOrderEntity entity = salesOrderMapper.selectOne(wrapper);
        if (entity == null) {
            return Optional.empty();
        }
        SalesOrder order = toDomain(entity);
        order.setLines(salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                        .eq(SalesOrderLineEntity::getOrderId, entity.getId())
                        .orderByAsc(SalesOrderLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(order);
    }

    @Override
    public String nextDocNo(LocalDate docDate) {
        long sequence = salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrderEntity>()
                .eq(SalesOrderEntity::getDocDate, docDate)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, docDate.format(DOC_DATE_FORMATTER), sequence);
    }

    @Override
    public void insert(SalesOrder order) {
        SalesOrderEntity entity = new SalesOrderEntity();
        fill(entity, order);
        salesOrderMapper.insert(entity);
        order.setId(entity.getId());
        for (SalesOrderLine line : order.getLines()) {
            SalesOrderLineEntity lineEntity = new SalesOrderLineEntity();
            lineEntity.setOrderId(entity.getId());
            fillLine(lineEntity, line);
            salesOrderLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(SalesOrder order) {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(order.getId());
        entity.setStatus(order.getStatus());
        entity.setShippedAmount(order.getShippedAmount());
        salesOrderMapper.updateById(entity);
        for (SalesOrderLine line : order.getLines()) {
            SalesOrderLineEntity lineEntity = salesOrderLineMapper.selectOne(
                    new LambdaQueryWrapper<SalesOrderLineEntity>()
                            .eq(SalesOrderLineEntity::getOrderId, order.getId())
                            .eq(SalesOrderLineEntity::getLineNo, line.getLineNo())
                            .last("limit 1"));
            if (lineEntity != null) {
                lineEntity.setShippedQuantity(line.getShippedQuantity());
                salesOrderLineMapper.updateById(lineEntity);
            }
        }
    }

    @Override
    public BigDecimal reservedAmount(Long customerId) {
        return salesOrderMapper.selectList(new LambdaQueryWrapper<SalesOrderEntity>()
                        .eq(SalesOrderEntity::getCustomerId, customerId)
                        .in(SalesOrderEntity::getStatus, ScmStatus.APPROVED, ScmStatus.PARTIALLY_SHIPPED))
                .stream()
                .map(this::unshippedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 计算订单未发货金额。
     *
     * @param entity 订单头
     * @return 订单总额减去已发货金额
     */
    private BigDecimal unshippedAmount(SalesOrderEntity entity) {
        BigDecimal total = entity.getTotalAmount() == null ? BigDecimal.ZERO : entity.getTotalAmount();
        BigDecimal shipped = entity.getShippedAmount() == null ? BigDecimal.ZERO : entity.getShippedAmount();
        return total.subtract(shipped);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<SalesOrderEntity> filter(ScmQuery query) {
        LambdaQueryWrapper<SalesOrderEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(SalesOrderEntity::getDocNo, query.getKeyword())
                    .or().like(SalesOrderEntity::getCustomerName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(SalesOrderEntity::getStatus, query.getStatus());
        }
        if (query.getPartyId() != null) {
            wrapper.eq(SalesOrderEntity::getCustomerId, query.getPartyId());
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(SalesOrderEntity::getWarehouseId, query.getWarehouseId());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(SalesOrderEntity::getDocDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(SalesOrderEntity::getDocDate, query.getToDate());
        }
        return wrapper.orderByDesc(SalesOrderEntity::getDocDate)
                .orderByDesc(SalesOrderEntity::getDocNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private SalesOrder toDomain(SalesOrderEntity entity) {
        SalesOrder order = new SalesOrder();
        order.setId(entity.getId());
        order.setDocNo(entity.getDocNo());
        order.setCustomerId(entity.getCustomerId());
        order.setCustomerName(entity.getCustomerName());
        order.setDocDate(entity.getDocDate());
        order.setDeliveryDate(entity.getDeliveryDate());
        order.setWarehouseId(entity.getWarehouseId());
        order.setStatus(entity.getStatus());
        order.setCurrency(entity.getCurrency());
        order.setTotalAmount(entity.getTotalAmount());
        order.setShippedAmount(entity.getShippedAmount());
        order.setRemark(entity.getRemark());
        order.setSourceType(entity.getSourceType());
        order.setSourceId(entity.getSourceId());
        order.setCrmOrderNo(entity.getCrmOrderNo());
        return order;
    }

    /**
     * 分录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private SalesOrderLine toDomain(SalesOrderLineEntity entity) {
        SalesOrderLine line = new SalesOrderLine();
        line.setLineNo(entity.getLineNo());
        line.setItemId(entity.getItemId());
        line.setItemCode(entity.getItemCode());
        line.setItemName(entity.getItemName());
        line.setUom(entity.getUom());
        line.setQuantity(entity.getQuantity());
        line.setShippedQuantity(entity.getShippedQuantity());
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
    private void fill(SalesOrderEntity entity, SalesOrder order) {
        entity.setDocNo(order.getDocNo());
        entity.setCustomerId(order.getCustomerId());
        entity.setCustomerName(order.getCustomerName());
        entity.setDocDate(order.getDocDate());
        entity.setDeliveryDate(order.getDeliveryDate());
        entity.setWarehouseId(order.getWarehouseId());
        entity.setStatus(order.getStatus());
        entity.setCurrency(order.getCurrency());
        entity.setTotalAmount(order.getTotalAmount());
        entity.setShippedAmount(order.getShippedAmount());
        entity.setRemark(order.getRemark());
        entity.setSourceType(order.getSourceType());
        entity.setSourceId(order.getSourceId());
        entity.setCrmOrderNo(order.getCrmOrderNo());
    }

    /**
     * 领域模型写入分录实体。
     *
     * @param entity 实体
     * @param line 领域模型
     */
    private void fillLine(SalesOrderLineEntity entity, SalesOrderLine line) {
        entity.setLineNo(line.getLineNo());
        entity.setItemId(line.getItemId());
        entity.setItemCode(line.getItemCode());
        entity.setItemName(line.getItemName());
        entity.setUom(line.getUom());
        entity.setQuantity(line.getQuantity());
        entity.setShippedQuantity(line.getShippedQuantity());
        entity.setUnitPrice(line.getUnitPrice());
        entity.setAmount(line.getAmount());
    }
}
