package com.bluewhale.crm.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.service.domain.model.ReturnRequest;
import com.bluewhale.crm.service.domain.model.ReturnRequestItem;
import com.bluewhale.crm.service.domain.model.ReturnRequestQuery;
import com.bluewhale.crm.service.domain.model.ReturnStatus;
import com.bluewhale.crm.service.domain.repository.ReturnRequestRepository;
import com.bluewhale.crm.service.infrastructure.persistence.entity.ReturnRequestEntity;
import com.bluewhale.crm.service.infrastructure.persistence.entity.ReturnRequestItemEntity;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.ReturnRequestItemMapper;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.ReturnRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 退换货申请仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ReturnRequestRepositoryImpl implements ReturnRequestRepository {

    /** 每页上限 */
    private static final int MAX_PAGE_SIZE = 100;

    private final ReturnRequestMapper returnRequestMapper;

    private final ReturnRequestItemMapper returnRequestItemMapper;

    @Override
    public List<ReturnRequest> page(ReturnRequestQuery query) {
        LambdaQueryWrapper<ReturnRequestEntity> wrapper = wrapper(query)
                .orderByDesc(ReturnRequestEntity::getUpdateTime)
                .orderByDesc(ReturnRequestEntity::getId)
                .last("limit " + offset(query) + ", " + size(query));
        return returnRequestMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ReturnRequestQuery query) {
        return returnRequestMapper.selectCount(wrapper(query));
    }

    @Override
    public ReturnRequest findById(Long id) {
        ReturnRequestEntity entity = returnRequestMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public List<ReturnRequestItem> findItems(Long requestId) {
        return returnRequestItemMapper.selectList(new LambdaQueryWrapper<ReturnRequestItemEntity>()
                        .eq(ReturnRequestItemEntity::getRequestId, requestId)
                        .orderByAsc(ReturnRequestItemEntity::getLineNo))
                .stream().map(this::toDomainItem).toList();
    }

    @Override
    public ReturnRequest findByApprovalInstanceId(Long instanceId) {
        ReturnRequestEntity entity = returnRequestMapper.selectOne(new LambdaQueryWrapper<ReturnRequestEntity>()
                .eq(ReturnRequestEntity::getApprovalInstanceId, instanceId)
                .last("limit 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(ReturnRequest request) {
        ReturnRequestEntity entity = toEntity(request);
        returnRequestMapper.insert(entity);
        request.setId(entity.getId());
        insertItems(request);
    }

    @Override
    public void replaceItems(ReturnRequest request) {
        // 行表挂 BaseEntity 的 @TableLogic：BaseMapper.delete 是逻辑删除，旧行仍物理存在，
        // 会顶住申请行唯一键导致重插冲突；明细行随主单留痕即可，改物理删除。
        returnRequestItemMapper.deletePhysicallyByRequestId(request.getId());
        insertItems(request);
    }

    @Override
    public boolean update(ReturnRequest request) {
        return returnRequestMapper.updateById(toEntity(request)) > 0;
    }

    @Override
    public boolean applyApprovalResult(Long requestId, boolean approved, LocalDateTime at) {
        String target = approved ? ReturnStatus.APPROVED.name() : ReturnStatus.REJECTED.name();
        return returnRequestMapper.update(null, new LambdaUpdateWrapper<ReturnRequestEntity>()
                .eq(ReturnRequestEntity::getId, requestId)
                .eq(ReturnRequestEntity::getStatus, ReturnStatus.PENDING_APPROVAL.name())
                .set(ReturnRequestEntity::getStatus, target)) > 0;
    }

    @Override
    public List<ReturnRequest> findByStatuses(List<String> statuses, int limit) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return returnRequestMapper.selectList(new LambdaQueryWrapper<ReturnRequestEntity>()
                        .in(ReturnRequestEntity::getStatus, statuses)
                        .orderByAsc(ReturnRequestEntity::getUpdateTime)
                        .last("limit " + Math.max(1, limit)))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long maxSequence(String prefix) {
        return returnRequestMapper.selectCount(new LambdaQueryWrapper<ReturnRequestEntity>()
                .likeRight(ReturnRequestEntity::getReturnNo, prefix));
    }

    @Override
    public BigDecimal sumReturnedQuantity(Long orderId, Integer orderLineNo, List<String> statuses) {
        if (orderId == null || orderLineNo == null || statuses == null || statuses.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> requestIds = returnRequestMapper.selectList(new LambdaQueryWrapper<ReturnRequestEntity>()
                        .select(ReturnRequestEntity::getId)
                        .eq(ReturnRequestEntity::getOrderId, orderId)
                        .in(ReturnRequestEntity::getStatus, statuses))
                .stream().map(ReturnRequestEntity::getId).filter(Objects::nonNull).toList();
        if (requestIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return returnRequestItemMapper.selectList(new LambdaQueryWrapper<ReturnRequestItemEntity>()
                        .in(ReturnRequestItemEntity::getRequestId, requestIds)
                        .eq(ReturnRequestItemEntity::getOrderLineNo, orderLineNo))
                .stream().map(ReturnRequestItemEntity::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 插入申请行。
     *
     * @param request 申请
     */
    private void insertItems(ReturnRequest request) {
        for (ReturnRequestItem item : request.getItems()) {
            ReturnRequestItemEntity entity = toEntityItem(item);
            entity.setRequestId(request.getId());
            returnRequestItemMapper.insert(entity);
            item.setId(entity.getId());
        }
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 条件包装器
     */
    private LambdaQueryWrapper<ReturnRequestEntity> wrapper(ReturnRequestQuery query) {
        ReturnRequestQuery safe = query == null ? new ReturnRequestQuery() : query;
        LambdaQueryWrapper<ReturnRequestEntity> wrapper = new LambdaQueryWrapper<ReturnRequestEntity>()
                .eq(StringUtils.hasText(safe.getStatus()), ReturnRequestEntity::getStatus, safe.getStatus())
                .eq(safe.getCustomerId() != null, ReturnRequestEntity::getCustomerId, safe.getCustomerId())
                .eq(safe.getOrderId() != null, ReturnRequestEntity::getOrderId, safe.getOrderId())
                .eq(safe.getOwnerId() != null, ReturnRequestEntity::getOwnerId, safe.getOwnerId());
        if (StringUtils.hasText(safe.getKeyword())) {
            String keyword = safe.getKeyword().trim();
            wrapper.and(w -> w.like(ReturnRequestEntity::getReturnNo, keyword)
                    .or().like(ReturnRequestEntity::getCustomerName, keyword)
                    .or().like(ReturnRequestEntity::getErpOrderNo, keyword));
        }
        return wrapper;
    }

    /**
     * 分页偏移。
     *
     * @param query 查询条件
     * @return 偏移
     */
    private long offset(ReturnRequestQuery query) {
        int current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        return (long) (current - 1) * size(query);
    }

    /**
     * 每页条数。
     *
     * @param query 查询条件
     * @return 条数
     */
    private int size(ReturnRequestQuery query) {
        int size = query.getSize() == null || query.getSize() < 1 ? 10 : query.getSize();
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 申请
     */
    private ReturnRequest toDomain(ReturnRequestEntity entity) {
        ReturnRequest request = new ReturnRequest();
        request.setId(entity.getId());
        request.setTenantId(entity.getTenantId());
        request.setReturnNo(entity.getReturnNo());
        request.setRequestType(entity.getRequestType());
        request.setOrderId(entity.getOrderId());
        request.setErpOrderNo(entity.getErpOrderNo());
        request.setCustomerId(entity.getCustomerId());
        request.setCustomerCode(entity.getCustomerCode());
        request.setCustomerName(entity.getCustomerName());
        request.setReason(entity.getReason());
        request.setStatus(ReturnStatus.from(entity.getStatus()));
        request.setApprovalInstanceId(entity.getApprovalInstanceId());
        request.setErpReturnNo(entity.getErpReturnNo());
        request.setErpStatus(entity.getErpStatus());
        request.setPushAttempts(entity.getPushAttempts());
        request.setLastError(entity.getLastError());
        request.setOwnerId(entity.getOwnerId());
        request.setDeptId(entity.getDeptId());
        request.setLockVersion(entity.getLockVersion());
        return request;
    }

    /**
     * 领域对象转实体。
     *
     * @param request 申请
     * @return 实体
     */
    private ReturnRequestEntity toEntity(ReturnRequest request) {
        ReturnRequestEntity entity = new ReturnRequestEntity();
        entity.setId(request.getId());
        entity.setTenantId(request.getTenantId());
        entity.setReturnNo(request.getReturnNo());
        entity.setRequestType(request.getRequestType());
        entity.setOrderId(request.getOrderId());
        entity.setErpOrderNo(request.getErpOrderNo());
        entity.setCustomerId(request.getCustomerId());
        entity.setCustomerCode(request.getCustomerCode());
        entity.setCustomerName(request.getCustomerName());
        entity.setReason(request.getReason());
        entity.setStatus(request.getStatus() == null ? null : request.getStatus().name());
        entity.setApprovalInstanceId(request.getApprovalInstanceId());
        entity.setErpReturnNo(request.getErpReturnNo());
        entity.setErpStatus(request.getErpStatus());
        entity.setPushAttempts(request.getPushAttempts());
        entity.setLastError(request.getLastError());
        entity.setOwnerId(request.getOwnerId());
        entity.setDeptId(request.getDeptId());
        entity.setLockVersion(request.getLockVersion());
        return entity;
    }

    /**
     * 行实体转领域对象。
     *
     * @param entity 行实体
     * @return 行
     */
    private ReturnRequestItem toDomainItem(ReturnRequestItemEntity entity) {
        ReturnRequestItem item = new ReturnRequestItem();
        item.setId(entity.getId());
        item.setRequestId(entity.getRequestId());
        item.setLineNo(entity.getLineNo());
        item.setOrderLineNo(entity.getOrderLineNo());
        item.setItemCode(entity.getItemCode());
        item.setItemName(entity.getItemName());
        item.setQuantity(entity.getQuantity());
        item.setUnitPrice(entity.getUnitPrice());
        item.setAmount(entity.getAmount());
        return item;
    }

    /**
     * 行领域对象转实体。
     *
     * @param item 行
     * @return 行实体
     */
    private ReturnRequestItemEntity toEntityItem(ReturnRequestItem item) {
        ReturnRequestItemEntity entity = new ReturnRequestItemEntity();
        entity.setId(item.getId());
        entity.setRequestId(item.getRequestId());
        entity.setLineNo(item.getLineNo());
        entity.setOrderLineNo(item.getOrderLineNo());
        entity.setItemCode(item.getItemCode());
        entity.setItemName(item.getItemName());
        entity.setQuantity(item.getQuantity());
        entity.setUnitPrice(item.getUnitPrice());
        entity.setAmount(item.getAmount());
        return entity;
    }
}
