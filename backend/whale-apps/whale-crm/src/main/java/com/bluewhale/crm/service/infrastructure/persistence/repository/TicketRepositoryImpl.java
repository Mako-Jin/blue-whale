package com.bluewhale.crm.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.service.domain.model.Ticket;
import com.bluewhale.crm.service.domain.model.TicketPriority;
import com.bluewhale.crm.service.domain.model.TicketQuery;
import com.bluewhale.crm.service.domain.model.TicketStatus;
import com.bluewhale.crm.service.domain.repository.TicketRepository;
import com.bluewhale.crm.service.infrastructure.persistence.entity.TicketEntity;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工单仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class TicketRepositoryImpl implements TicketRepository {

    private final TicketMapper ticketMapper;

    @Override
    public List<Ticket> page(TicketQuery query) {
        Page<TicketEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return ticketMapper.selectPage(page, pageWrapper(query))
                .getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(TicketQuery query) {
        return ticketMapper.selectCount(pageWrapper(query));
    }

    @Override
    public Ticket findById(Long id) {
        TicketEntity entity = ticketMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(Ticket ticket) {
        TicketEntity entity = toEntity(ticket);
        ticketMapper.insert(entity);
        ticket.setId(entity.getId());
    }

    @Override
    public void update(Ticket ticket) {
        ticketMapper.updateById(toEntity(ticket));
    }

    @Override
    public long countByPrefix(String prefix) {
        return ticketMapper.selectCount(new LambdaQueryWrapper<TicketEntity>()
                .likeRight(TicketEntity::getTicketNo, prefix));
    }

    /**
     * 构造分页查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<TicketEntity> pageWrapper(TicketQuery query) {
        LambdaQueryWrapper<TicketEntity> wrapper = new LambdaQueryWrapper<TicketEntity>()
                .eq(StringUtils.hasText(query.getStatus()), TicketEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getPriority()), TicketEntity::getPriority, query.getPriority())
                .eq(query.getCustomerId() != null, TicketEntity::getCustomerId, query.getCustomerId())
                .eq(query.getAssigneeId() != null, TicketEntity::getAssigneeId, query.getAssigneeId())
                .orderByDesc(TicketEntity::getCreateTime);
        if (query.getScopeUserId() != null) {
            wrapper.and(w -> w.eq(TicketEntity::getOwnerId, query.getScopeUserId())
                    .or().eq(TicketEntity::getAssigneeId, query.getScopeUserId()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(TicketEntity::getTicketNo, keyword)
                    .or().like(TicketEntity::getTitle, keyword)
                    .or().like(TicketEntity::getCustomerName, keyword));
        }
        return wrapper;
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 工单
     */
    private Ticket toDomain(TicketEntity entity) {
        Ticket ticket = new Ticket();
        ticket.setId(entity.getId());
        ticket.setTenantId(entity.getTenantId());
        ticket.setTicketNo(entity.getTicketNo());
        ticket.setCustomerId(entity.getCustomerId());
        ticket.setCustomerCode(entity.getCustomerCode());
        ticket.setCustomerName(entity.getCustomerName());
        ticket.setContactId(entity.getContactId());
        ticket.setContactName(entity.getContactName());
        ticket.setContractId(entity.getContractId());
        ticket.setOrderId(entity.getOrderId());
        ticket.setErpOrderNo(entity.getErpOrderNo());
        ticket.setProductCode(entity.getProductCode());
        ticket.setSerialNo(entity.getSerialNo());
        ticket.setCategory(entity.getCategory());
        ticket.setPriority(priorityOf(entity.getPriority()));
        ticket.setTitle(entity.getTitle());
        ticket.setDescription(entity.getDescription());
        ticket.setStatus(statusOf(entity.getStatus()));
        ticket.setAssigneeId(entity.getAssigneeId());
        ticket.setAssigneeName(entity.getAssigneeName());
        ticket.setOwnerId(entity.getOwnerId());
        ticket.setDeptId(entity.getDeptId());
        ticket.setResponseDeadline(entity.getResponseDeadline());
        ticket.setResolveDeadline(entity.getResolveDeadline());
        ticket.setFirstRespondedAt(entity.getFirstRespondedAt());
        ticket.setPausedAt(entity.getPausedAt());
        ticket.setPausedSeconds(entity.getPausedSeconds());
        ticket.setResolvedAt(entity.getResolvedAt());
        ticket.setClosedAt(entity.getClosedAt());
        ticket.setSolution(entity.getSolution());
        ticket.setReopenCount(entity.getReopenCount());
        ticket.setLockVersion(entity.getLockVersion());
        ticket.setCreateTime(entity.getCreateTime());
        ticket.setUpdateTime(entity.getUpdateTime());
        return ticket;
    }

    /**
     * 领域对象转实体。
     *
     * @param ticket 工单
     * @return 实体
     */
    private TicketEntity toEntity(Ticket ticket) {
        TicketEntity entity = new TicketEntity();
        entity.setId(ticket.getId());
        entity.setTenantId(ticket.getTenantId());
        entity.setTicketNo(ticket.getTicketNo());
        entity.setCustomerId(ticket.getCustomerId());
        entity.setCustomerCode(ticket.getCustomerCode());
        entity.setCustomerName(ticket.getCustomerName());
        entity.setContactId(ticket.getContactId());
        entity.setContactName(ticket.getContactName());
        entity.setContractId(ticket.getContractId());
        entity.setOrderId(ticket.getOrderId());
        entity.setErpOrderNo(ticket.getErpOrderNo());
        entity.setProductCode(ticket.getProductCode());
        entity.setSerialNo(ticket.getSerialNo());
        entity.setCategory(ticket.getCategory());
        entity.setPriority(ticket.getPriority() == null ? null : ticket.getPriority().name());
        entity.setTitle(ticket.getTitle());
        entity.setDescription(ticket.getDescription());
        entity.setStatus(ticket.getStatus() == null ? null : ticket.getStatus().name());
        entity.setAssigneeId(ticket.getAssigneeId());
        entity.setAssigneeName(ticket.getAssigneeName());
        entity.setOwnerId(ticket.getOwnerId());
        entity.setDeptId(ticket.getDeptId());
        entity.setResponseDeadline(ticket.getResponseDeadline());
        entity.setResolveDeadline(ticket.getResolveDeadline());
        entity.setFirstRespondedAt(ticket.getFirstRespondedAt());
        entity.setPausedAt(ticket.getPausedAt());
        entity.setPausedSeconds(ticket.getPausedSeconds());
        entity.setResolvedAt(ticket.getResolvedAt());
        entity.setClosedAt(ticket.getClosedAt());
        entity.setSolution(ticket.getSolution());
        entity.setReopenCount(ticket.getReopenCount());
        entity.setLockVersion(ticket.getLockVersion());
        return entity;
    }

    /**
     * 解析优先级。
     *
     * @param value 文本
     * @return 优先级
     */
    private TicketPriority priorityOf(String value) {
        return value == null ? null : TicketPriority.from(value);
    }

    /**
     * 解析状态。
     *
     * @param value 文本
     * @return 状态
     */
    private TicketStatus statusOf(String value) {
        return value == null ? null : TicketStatus.from(value);
    }
}
