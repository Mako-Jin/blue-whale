package com.bluewhale.common.domain.model;

import com.bluewhale.common.domain.event.DomainEvent;

import java.util.Collection;

/**
 * 聚合根接口：在一组实体与值对象之上维持一致性边界，并收集本次变更产生的领域事件。
 *
 * @param <ID> 聚合根主键类型
 * @author BlueWhale
 */
public interface Aggregate<ID> extends Entity<ID> {

    /**
     * 获取当前聚合尚未发布的领域事件
     *
     * @return 领域事件集合的只读视图；无待发布事件时返回空集合
     */
    Collection<DomainEvent> getDomainEvents();

    /**
     * 清空当前聚合已收集的领域事件（通常在发布成功后调用）
     */
    void clearDomainEvents();
}
