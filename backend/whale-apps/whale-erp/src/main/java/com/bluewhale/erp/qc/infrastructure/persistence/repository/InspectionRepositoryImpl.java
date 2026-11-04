package com.bluewhale.erp.qc.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.qc.domain.model.InspectionItem;
import com.bluewhale.erp.qc.domain.model.InspectionOrder;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.repository.InspectionRepository;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.InspectionItemEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.InspectionOrderEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.InspectionItemMapper;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.InspectionOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 质量检验单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class InspectionRepositoryImpl implements InspectionRepository {

    /** 检验单号前缀 */
    private static final String DOC_PREFIX = "QC-";

    /** 单号日期格式 */
    private static final DateTimeFormatter DOC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InspectionOrderMapper inspectionOrderMapper;

    private final InspectionItemMapper inspectionItemMapper;

    @Override
    public List<InspectionOrder> page(QcQuery query) {
        Page<InspectionOrderEntity> page = inspectionOrderMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(QcQuery query) {
        return inspectionOrderMapper.selectCount(filter(query));
    }

    @Override
    public Optional<InspectionOrder> findById(Long id) {
        InspectionOrderEntity entity = inspectionOrderMapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(withItems(toDomain(entity)));
    }

    @Override
    public Optional<InspectionOrder> findByOrderNo(String orderNo) {
        InspectionOrderEntity entity = inspectionOrderMapper.selectOne(new LambdaQueryWrapper<InspectionOrderEntity>()
                .eq(InspectionOrderEntity::getOrderNo, orderNo)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(withItems(toDomain(entity)));
    }

    @Override
    public String nextOrderNo(LocalDate inspectDate) {
        long sequence = inspectionOrderMapper.selectCount(new LambdaQueryWrapper<InspectionOrderEntity>()
                .eq(InspectionOrderEntity::getInspectDate, inspectDate)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, inspectDate.format(DOC_DATE_FORMATTER), sequence);
    }

    @Override
    public void insert(InspectionOrder order) {
        InspectionOrderEntity entity = new InspectionOrderEntity();
        fill(entity, order);
        inspectionOrderMapper.insert(entity);
        order.setId(entity.getId());
        for (InspectionItem item : order.getItems()) {
            InspectionItemEntity itemEntity = new InspectionItemEntity();
            itemEntity.setOrderId(entity.getId());
            fillItem(itemEntity, item);
            inspectionItemMapper.insert(itemEntity);
        }
    }

    @Override
    public void update(InspectionOrder order) {
        InspectionOrderEntity entity = new InspectionOrderEntity();
        entity.setId(order.getId());
        entity.setStatus(order.getStatus());
        entity.setInspectedQuantity(order.getInspectedQuantity());
        entity.setInspector(order.getInspector());
        entity.setToolId(order.getToolId());
        entity.setToolNo(order.getToolNo());
        entity.setNcrId(order.getNcrId());
        entity.setRemark(order.getRemark());
        inspectionOrderMapper.updateById(entity);
        for (InspectionItem item : order.getItems()) {
            InspectionItemEntity itemEntity = inspectionItemMapper.selectOne(
                    new LambdaQueryWrapper<InspectionItemEntity>()
                            .eq(InspectionItemEntity::getOrderId, order.getId())
                            .eq(InspectionItemEntity::getLineNo, item.getLineNo())
                            .last("limit 1"));
            if (itemEntity != null) {
                itemEntity.setActualValue(item.getActualValue());
                itemEntity.setResult(item.getResult());
                itemEntity.setRemark(item.getRemark());
                inspectionItemMapper.updateById(itemEntity);
            }
        }
    }

    /**
     * 加载检验项。
     *
     * @param order 检验单
     * @return 含检验项的检验单
     */
    private InspectionOrder withItems(InspectionOrder order) {
        order.setItems(inspectionItemMapper.selectList(new LambdaQueryWrapper<InspectionItemEntity>()
                        .eq(InspectionItemEntity::getOrderId, order.getId())
                        .orderByAsc(InspectionItemEntity::getLineNo))
                .stream().map(this::toItem).toList());
        return order;
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<InspectionOrderEntity> filter(QcQuery query) {
        LambdaQueryWrapper<InspectionOrderEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(InspectionOrderEntity::getOrderNo, query.getKeyword())
                    .or().like(InspectionOrderEntity::getItemCode, query.getKeyword())
                    .or().like(InspectionOrderEntity::getItemName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(InspectionOrderEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getInspectionType())) {
            wrapper.eq(InspectionOrderEntity::getInspectionType, query.getInspectionType());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(InspectionOrderEntity::getSourceType, query.getSourceType());
        }
        if (StringUtils.hasText(query.getItemCode())) {
            wrapper.eq(InspectionOrderEntity::getItemCode, query.getItemCode());
        }
        if (StringUtils.hasText(query.getLotNo())) {
            wrapper.eq(InspectionOrderEntity::getLotNo, query.getLotNo());
        }
        if (StringUtils.hasText(query.getSupplierCode())) {
            wrapper.eq(InspectionOrderEntity::getSupplierCode, query.getSupplierCode());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(InspectionOrderEntity::getInspectDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(InspectionOrderEntity::getInspectDate, query.getToDate());
        }
        return wrapper.orderByDesc(InspectionOrderEntity::getInspectDate)
                .orderByDesc(InspectionOrderEntity::getOrderNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private InspectionOrder toDomain(InspectionOrderEntity entity) {
        InspectionOrder order = new InspectionOrder();
        order.setId(entity.getId());
        order.setOrderNo(entity.getOrderNo());
        order.setInspectionType(entity.getInspectionType());
        order.setSourceType(entity.getSourceType());
        order.setSourceNo(entity.getSourceNo());
        order.setItemId(entity.getItemId());
        order.setItemCode(entity.getItemCode());
        order.setItemName(entity.getItemName());
        order.setLotNo(entity.getLotNo());
        order.setQuantity(entity.getQuantity());
        order.setInspectedQuantity(entity.getInspectedQuantity());
        order.setSupplierCode(entity.getSupplierCode());
        order.setStatus(entity.getStatus());
        order.setInspector(entity.getInspector());
        order.setInspectDate(entity.getInspectDate());
        order.setToolId(entity.getToolId());
        order.setToolNo(entity.getToolNo());
        order.setNcrId(entity.getNcrId());
        order.setRemark(entity.getRemark());
        return order;
    }

    /**
     * 检验项实体转领域模型。
     *
     * @param entity 实体
     * @return 检验项
     */
    private InspectionItem toItem(InspectionItemEntity entity) {
        InspectionItem item = new InspectionItem();
        item.setLineNo(entity.getLineNo());
        item.setItemName(entity.getItemName());
        item.setStandardValue(entity.getStandardValue());
        item.setActualValue(entity.getActualValue());
        item.setResult(entity.getResult());
        item.setRemark(entity.getRemark());
        return item;
    }

    /**
     * 领域模型写入单头实体。
     *
     * @param entity 实体
     * @param order 检验单
     */
    private void fill(InspectionOrderEntity entity, InspectionOrder order) {
        entity.setOrderNo(order.getOrderNo());
        entity.setInspectionType(order.getInspectionType());
        entity.setSourceType(order.getSourceType());
        entity.setSourceNo(order.getSourceNo());
        entity.setItemId(order.getItemId());
        entity.setItemCode(order.getItemCode());
        entity.setItemName(order.getItemName());
        entity.setLotNo(order.getLotNo());
        entity.setQuantity(order.getQuantity());
        entity.setInspectedQuantity(order.getInspectedQuantity());
        entity.setSupplierCode(order.getSupplierCode());
        entity.setStatus(order.getStatus());
        entity.setInspector(order.getInspector());
        entity.setInspectDate(order.getInspectDate());
        entity.setToolId(order.getToolId());
        entity.setToolNo(order.getToolNo());
        entity.setNcrId(order.getNcrId());
        entity.setRemark(order.getRemark());
    }

    /**
     * 领域模型写入检验项实体。
     *
     * @param entity 实体
     * @param item 检验项
     */
    private void fillItem(InspectionItemEntity entity, InspectionItem item) {
        entity.setLineNo(item.getLineNo());
        entity.setItemName(item.getItemName());
        entity.setStandardValue(item.getStandardValue());
        entity.setActualValue(item.getActualValue());
        entity.setResult(item.getResult());
        entity.setRemark(item.getRemark());
    }
}
