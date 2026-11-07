package com.bluewhale.crm.analytics.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.analytics.domain.model.ActionItem;
import com.bluewhale.crm.analytics.domain.model.ActionItemQuery;
import com.bluewhale.crm.analytics.domain.model.ActionItemStatus;
import com.bluewhale.crm.analytics.domain.model.ActionType;
import com.bluewhale.crm.analytics.domain.repository.ActionItemRepository;
import com.bluewhale.crm.analytics.infrastructure.persistence.entity.ActionItemEntity;
import com.bluewhale.crm.analytics.infrastructure.persistence.mapper.ActionItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 行动项仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ActionItemRepositoryImpl implements ActionItemRepository {

    private final ActionItemMapper actionItemMapper;

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 行动项
     */
    @Override
    public ActionItem findById(Long id) {
        ActionItemEntity entity = actionItemMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    /**
     * 按去重键查询（含已关闭）。
     *
     * @param dedupKey 去重键
     * @return 行动项
     */
    @Override
    public ActionItem findByDedupKey(String dedupKey) {
        List<ActionItemEntity> entities = actionItemMapper.selectList(new LambdaQueryWrapper<ActionItemEntity>()
                .eq(ActionItemEntity::getDedupKey, dedupKey)
                .last("LIMIT 1"));
        return entities.isEmpty() ? null : toDomain(entities.get(0));
    }

    /**
     * 按去重键查询未关闭行动项。
     *
     * @param dedupKey 去重键
     * @return 行动项
     */
    @Override
    public ActionItem findOpenByDedupKey(String dedupKey) {
        List<ActionItemEntity> entities = actionItemMapper.selectList(new LambdaQueryWrapper<ActionItemEntity>()
                .eq(ActionItemEntity::getDedupKey, dedupKey)
                .eq(ActionItemEntity::getStatus, ActionItemStatus.OPEN.name())
                .last("LIMIT 1"));
        return entities.isEmpty() ? null : toDomain(entities.get(0));
    }

    /**
     * 按规则编码查询全部行动项。
     *
     * @param ruleCode 规则编码
     * @return 行动项列表
     */
    @Override
    public List<ActionItem> findByRuleCode(String ruleCode) {
        return actionItemMapper.selectList(new LambdaQueryWrapper<ActionItemEntity>()
                .eq(ActionItemEntity::getRuleCode, ruleCode)
                .orderByDesc(ActionItemEntity::getCreateTime))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 新增行动项。
     *
     * @param item 行动项
     */
    @Override
    public void insert(ActionItem item) {
        ActionItemEntity entity = toEntity(item);
        actionItemMapper.insert(entity);
        item.setId(entity.getId());
    }

    /**
     * 更新行动项。
     *
     * @param item 行动项
     */
    @Override
    public void update(ActionItem item) {
        actionItemMapper.updateById(toEntity(item));
    }

    /**
     * 分页查询。
     *
     * @param query 查询条件
     * @return 行动项列表
     */
    @Override
    public List<ActionItem> page(ActionItemQuery query) {
        Page<ActionItemEntity> page = new Page<>(query.getCurrent(), query.getSize());
        return actionItemMapper.selectPage(page, pageWrapper(query))
                .getRecords().stream().map(this::toDomain).toList();
    }

    /**
     * 查询总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    @Override
    public long count(ActionItemQuery query) {
        return actionItemMapper.selectCount(pageWrapper(query));
    }

    /**
     * 统计未关闭行动项数量。
     *
     * @return 未关闭数量
     */
    @Override
    public long countOpen() {
        return actionItemMapper.selectCount(new LambdaQueryWrapper<ActionItemEntity>()
                .eq(ActionItemEntity::getStatus, ActionItemStatus.OPEN.name()));
    }

    /**
     * 构造分页查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<ActionItemEntity> pageWrapper(ActionItemQuery query) {
        return new LambdaQueryWrapper<ActionItemEntity>()
                .eq(StringUtils.hasText(query.getRuleCode()), ActionItemEntity::getRuleCode, query.getRuleCode())
                .eq(StringUtils.hasText(query.getStatus()), ActionItemEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getPriority()), ActionItemEntity::getPriority, query.getPriority())
                .eq(StringUtils.hasText(query.getRefType()), ActionItemEntity::getRefType, query.getRefType())
                .eq(query.getCustomerId() != null, ActionItemEntity::getCustomerId, query.getCustomerId())
                .eq(query.getOwnerId() != null, ActionItemEntity::getOwnerId, query.getOwnerId())
                .eq(query.getScopeOwnerId() != null, ActionItemEntity::getOwnerId, query.getScopeOwnerId())
                .orderByAsc(ActionItemEntity::getPriority)
                .orderByAsc(ActionItemEntity::getDueDate)
                .orderByDesc(ActionItemEntity::getCreateTime);
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 行动项
     */
    private ActionItem toDomain(ActionItemEntity entity) {
        ActionItem item = new ActionItem();
        item.setId(entity.getId());
        item.setTenantId(entity.getTenantId());
        item.setRuleCode(entity.getRuleCode());
        item.setRefType(ActionType.from(entity.getRefType()));
        item.setRefId(entity.getRefId());
        item.setCustomerId(entity.getCustomerId());
        item.setCustomerName(entity.getCustomerName());
        item.setTitle(entity.getTitle());
        item.setSuggestion(entity.getSuggestion());
        item.setPriority(entity.getPriority());
        item.setOwnerId(entity.getOwnerId());
        item.setDeptId(entity.getDeptId());
        item.setDueDate(entity.getDueDate());
        item.setStatus(ActionItemStatus.from(entity.getStatus()));
        item.setCloseReason(entity.getCloseReason());
        item.setClosedBy(entity.getClosedBy());
        item.setClosedAt(entity.getClosedAt());
        item.setDedupKey(entity.getDedupKey());
        item.setCreateTime(entity.getCreateTime());
        item.setUpdateTime(entity.getUpdateTime());
        return item;
    }

    /**
     * 领域对象转实体。
     *
     * @param item 行动项
     * @return 实体
     */
    private ActionItemEntity toEntity(ActionItem item) {
        ActionItemEntity entity = new ActionItemEntity();
        entity.setId(item.getId());
        entity.setTenantId(item.getTenantId());
        entity.setRuleCode(item.getRuleCode());
        entity.setRefType(item.getRefType() == null ? null : item.getRefType().name());
        entity.setRefId(item.getRefId());
        entity.setCustomerId(item.getCustomerId());
        entity.setCustomerName(item.getCustomerName());
        entity.setTitle(item.getTitle());
        entity.setSuggestion(item.getSuggestion());
        entity.setPriority(item.getPriority());
        entity.setOwnerId(item.getOwnerId());
        entity.setDeptId(item.getDeptId());
        entity.setDueDate(item.getDueDate());
        entity.setStatus(item.getStatus() == null ? null : item.getStatus().name());
        entity.setCloseReason(item.getCloseReason());
        entity.setClosedBy(item.getClosedBy());
        entity.setClosedAt(item.getClosedAt());
        entity.setDedupKey(item.getDedupKey());
        return entity;
    }
}
