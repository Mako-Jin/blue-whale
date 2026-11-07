package com.bluewhale.crm.commerce.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.commerce.domain.model.InvoiceItem;
import com.bluewhale.crm.commerce.domain.model.InvoiceQuery;
import com.bluewhale.crm.commerce.domain.model.InvoiceRequest;
import com.bluewhale.crm.commerce.domain.model.InvoiceRequestType;
import com.bluewhale.crm.commerce.domain.model.InvoiceStatus;
import com.bluewhale.crm.commerce.domain.repository.InvoiceRequestRepository;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.InvoiceItemEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.entity.InvoiceRequestEntity;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.InvoiceItemMapper;
import com.bluewhale.crm.commerce.infrastructure.persistence.mapper.InvoiceRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 开票申请仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class InvoiceRequestRepositoryImpl implements InvoiceRequestRepository {

    private static final List<String> OCCUPIED_STATUSES = List.of(
            InvoiceStatus.PENDING_APPROVAL.name(), InvoiceStatus.APPROVED.name(),
            InvoiceStatus.ISSUED.name(), InvoiceStatus.SENT.name(), InvoiceStatus.RED_FLUSHED.name());

    private final InvoiceRequestMapper invoiceRequestMapper;

    private final InvoiceItemMapper invoiceItemMapper;

    @Override
    public List<InvoiceRequest> page(InvoiceQuery query) {
        Page<InvoiceRequestEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return invoiceRequestMapper.selectPage(page, pageWrapper(query))
                .getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(InvoiceQuery query) {
        return invoiceRequestMapper.selectCount(pageWrapper(query));
    }

    @Override
    public InvoiceRequest findById(Long id) {
        InvoiceRequestEntity entity = invoiceRequestMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public List<InvoiceItem> findItems(Long requestId) {
        return invoiceItemMapper.selectList(new LambdaQueryWrapper<InvoiceItemEntity>()
                        .eq(InvoiceItemEntity::getRequestId, requestId)
                        .orderByAsc(InvoiceItemEntity::getLineNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public InvoiceRequest findByApprovalInstanceId(Long instanceId) {
        InvoiceRequestEntity entity = invoiceRequestMapper.selectOne(new LambdaQueryWrapper<InvoiceRequestEntity>()
                .eq(InvoiceRequestEntity::getApprovalInstanceId, instanceId)
                .last("limit 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(InvoiceRequest request) {
        InvoiceRequestEntity entity = toEntity(request);
        invoiceRequestMapper.insert(entity);
        request.setId(entity.getId());
        insertItems(request);
    }

    @Override
    public void replaceItems(InvoiceRequest request) {
        invoiceItemMapper.delete(new LambdaQueryWrapper<InvoiceItemEntity>()
                .eq(InvoiceItemEntity::getRequestId, request.getId()));
        insertItems(request);
    }

    /**
     * 插入申请行。
     *
     * @param request 申请
     */
    private void insertItems(InvoiceRequest request) {
        for (InvoiceItem item : request.getItems()) {
            InvoiceItemEntity itemEntity = toEntity(item);
            itemEntity.setRequestId(request.getId());
            invoiceItemMapper.insert(itemEntity);
            item.setId(itemEntity.getId());
        }
    }

    @Override
    public boolean update(InvoiceRequest request) {
        return invoiceRequestMapper.updateById(toEntity(request)) > 0;
    }

    @Override
    public boolean applyApprovalResult(Long requestId, boolean approved, LocalDateTime at) {
        String target = approved ? InvoiceStatus.APPROVED.name() : InvoiceStatus.REJECTED.name();
        LambdaUpdateWrapper<InvoiceRequestEntity> wrapper = new LambdaUpdateWrapper<InvoiceRequestEntity>()
                .eq(InvoiceRequestEntity::getId, requestId)
                .eq(InvoiceRequestEntity::getStatus, InvoiceStatus.PENDING_APPROVAL.name())
                .set(InvoiceRequestEntity::getStatus, target);
        if (approved) {
            wrapper.set(InvoiceRequestEntity::getApprovedAt, at);
        }
        return invoiceRequestMapper.update(null, wrapper) > 0;
    }

    @Override
    public BigDecimal sumOccupiedAmount(String sourceType, Long sourceId) {
        if (!StringUtils.hasText(sourceType) || sourceId == null) {
            return BigDecimal.ZERO;
        }
        return invoiceRequestMapper.selectList(new LambdaQueryWrapper<InvoiceRequestEntity>()
                        .eq(InvoiceRequestEntity::getSourceType, sourceType)
                        .eq(InvoiceRequestEntity::getSourceId, sourceId)
                        .in(InvoiceRequestEntity::getStatus, OCCUPIED_STATUSES))
                .stream()
                .map(InvoiceRequestEntity::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal sumRedFlushed(Long originInvoiceId) {
        if (originInvoiceId == null) {
            return BigDecimal.ZERO;
        }
        return invoiceRequestMapper.selectList(new LambdaQueryWrapper<InvoiceRequestEntity>()
                        .eq(InvoiceRequestEntity::getOriginInvoiceId, originInvoiceId)
                        .eq(InvoiceRequestEntity::getRequestType, InvoiceRequestType.RED_FLUSH.name())
                        .eq(InvoiceRequestEntity::getStatus, InvoiceStatus.RED_FLUSHED.name()))
                .stream()
                .map(InvoiceRequestEntity::getAmount)
                .filter(java.util.Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public long maxSequence(String prefix) {
        return invoiceRequestMapper.selectCount(new LambdaQueryWrapper<InvoiceRequestEntity>()
                .likeRight(InvoiceRequestEntity::getInvoiceNo, prefix));
    }

    /**
     * 构造分页查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<InvoiceRequestEntity> pageWrapper(InvoiceQuery query) {
        LambdaQueryWrapper<InvoiceRequestEntity> wrapper = new LambdaQueryWrapper<InvoiceRequestEntity>()
                .eq(StringUtils.hasText(query.getStatus()), InvoiceRequestEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getRequestType()), InvoiceRequestEntity::getRequestType,
                        query.getRequestType())
                .eq(query.getCustomerId() != null, InvoiceRequestEntity::getCustomerId, query.getCustomerId())
                .eq(query.getContractId() != null, InvoiceRequestEntity::getContractId, query.getContractId())
                .eq(query.getOwnerId() != null, InvoiceRequestEntity::getOwnerId, query.getOwnerId())
                .orderByDesc(InvoiceRequestEntity::getCreateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(InvoiceRequestEntity::getInvoiceNo, keyword)
                    .or().like(InvoiceRequestEntity::getInvoiceNumber, keyword)
                    .or().like(InvoiceRequestEntity::getCustomerName, keyword));
        }
        return wrapper;
    }

    /**
     * 实体转申请聚合（不含行）。
     *
     * @param entity 实体
     * @return 申请
     */
    private InvoiceRequest toDomain(InvoiceRequestEntity entity) {
        InvoiceRequest request = new InvoiceRequest();
        request.setId(entity.getId());
        request.setTenantId(entity.getTenantId());
        request.setInvoiceNo(entity.getInvoiceNo());
        request.setRequestType(InvoiceRequestType.from(entity.getRequestType()));
        request.setOriginInvoiceId(entity.getOriginInvoiceId());
        request.setSourceType(entity.getSourceType());
        request.setSourceId(entity.getSourceId());
        request.setSourceOrderNo(entity.getSourceOrderNo());
        request.setContractId(entity.getContractId());
        request.setContractNo(entity.getContractNo());
        request.setCustomerId(entity.getCustomerId());
        request.setCustomerCode(entity.getCustomerCode());
        request.setCustomerName(entity.getCustomerName());
        request.setCurrency(entity.getCurrency());
        request.setAmount(entity.getAmount());
        request.setTaxAmount(entity.getTaxAmount());
        request.setInvoiceType(entity.getInvoiceType());
        request.setTitleType(entity.getTitleType());
        request.setInvoiceTitle(entity.getInvoiceTitle());
        request.setTaxNo(entity.getTaxNo());
        request.setStatus(InvoiceStatus.from(entity.getStatus()));
        request.setApprovalInstanceId(entity.getApprovalInstanceId());
        request.setSubmittedAt(entity.getSubmittedAt());
        request.setApprovedAt(entity.getApprovedAt());
        request.setInvoiceCode(entity.getInvoiceCode());
        request.setInvoiceNumber(entity.getInvoiceNumber());
        request.setIssuedAt(entity.getIssuedAt());
        request.setIssuedAmount(entity.getIssuedAmount());
        request.setCourier(entity.getCourier());
        request.setTrackingNo(entity.getTrackingNo());
        request.setSentAt(entity.getSentAt());
        request.setRedFlushReason(entity.getRedFlushReason());
        request.setRedFlushedAt(entity.getRedFlushedAt());
        request.setOwnerId(entity.getOwnerId());
        request.setDeptId(entity.getDeptId());
        request.setRemark(entity.getRemark());
        request.setLockVersion(entity.getLockVersion());
        return request;
    }

    /**
     * 申请聚合转实体。
     *
     * @param request 申请
     * @return 实体
     */
    private InvoiceRequestEntity toEntity(InvoiceRequest request) {
        InvoiceRequestEntity entity = new InvoiceRequestEntity();
        entity.setId(request.getId());
        entity.setTenantId(request.getTenantId());
        entity.setInvoiceNo(request.getInvoiceNo());
        entity.setRequestType(request.getRequestType() == null ? null : request.getRequestType().name());
        entity.setOriginInvoiceId(request.getOriginInvoiceId());
        entity.setSourceType(request.getSourceType());
        entity.setSourceId(request.getSourceId());
        entity.setSourceOrderNo(request.getSourceOrderNo());
        entity.setContractId(request.getContractId());
        entity.setContractNo(request.getContractNo());
        entity.setCustomerId(request.getCustomerId());
        entity.setCustomerCode(request.getCustomerCode());
        entity.setCustomerName(request.getCustomerName());
        entity.setCurrency(request.getCurrency());
        entity.setAmount(request.getAmount());
        entity.setTaxAmount(request.getTaxAmount());
        entity.setInvoiceType(request.getInvoiceType());
        entity.setTitleType(request.getTitleType());
        entity.setInvoiceTitle(request.getInvoiceTitle());
        entity.setTaxNo(request.getTaxNo());
        entity.setStatus(request.getStatus() == null ? null : request.getStatus().name());
        entity.setApprovalInstanceId(request.getApprovalInstanceId());
        entity.setSubmittedAt(request.getSubmittedAt());
        entity.setApprovedAt(request.getApprovedAt());
        entity.setInvoiceCode(request.getInvoiceCode());
        entity.setInvoiceNumber(request.getInvoiceNumber());
        entity.setIssuedAt(request.getIssuedAt());
        entity.setIssuedAmount(request.getIssuedAmount());
        entity.setCourier(request.getCourier());
        entity.setTrackingNo(request.getTrackingNo());
        entity.setSentAt(request.getSentAt());
        entity.setRedFlushReason(request.getRedFlushReason());
        entity.setRedFlushedAt(request.getRedFlushedAt());
        entity.setOwnerId(request.getOwnerId());
        entity.setDeptId(request.getDeptId());
        entity.setRemark(request.getRemark());
        entity.setLockVersion(request.getLockVersion());
        return entity;
    }

    /**
     * 行实体转领域对象。
     *
     * @param entity 行实体
     * @return 申请行
     */
    private InvoiceItem toDomain(InvoiceItemEntity entity) {
        InvoiceItem item = new InvoiceItem();
        item.setId(entity.getId());
        item.setRequestId(entity.getRequestId());
        item.setLineNo(entity.getLineNo());
        item.setItemName(entity.getItemName());
        item.setSpecification(entity.getSpecification());
        item.setQuantity(entity.getQuantity());
        item.setUnitPrice(entity.getUnitPrice());
        item.setTaxRate(entity.getTaxRate());
        item.setTaxAmount(entity.getTaxAmount());
        item.setAmount(entity.getAmount());
        item.setRemark(entity.getRemark());
        return item;
    }

    /**
     * 领域行转实体。
     *
     * @param item 申请行
     * @return 行实体
     */
    private InvoiceItemEntity toEntity(InvoiceItem item) {
        InvoiceItemEntity entity = new InvoiceItemEntity();
        entity.setId(item.getId());
        entity.setRequestId(item.getRequestId());
        entity.setLineNo(item.getLineNo());
        entity.setItemName(item.getItemName());
        entity.setSpecification(item.getSpecification());
        entity.setQuantity(item.getQuantity());
        entity.setUnitPrice(item.getUnitPrice());
        entity.setTaxRate(item.getTaxRate());
        entity.setTaxAmount(item.getTaxAmount());
        entity.setAmount(item.getAmount());
        entity.setRemark(item.getRemark());
        return entity;
    }
}
