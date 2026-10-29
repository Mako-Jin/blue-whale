package com.bluewhale.common.domain.model;

import com.bluewhale.common.domain.event.DomainEvent;
import com.bluewhale.common.domain.event.publisher.DomainEventPublisher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 抽象聚合根：统一承载主键与领域事件收集、发布逻辑。
 *
 * <p>子类在业务方法内调用 {@link #registerEvent(DomainEvent)} 登记事件，由应用服务在事务提交前后
 * 调用 {@link #publishEvents(DomainEventPublisher)} 发布并清空，避免领域层直接依赖消息或缓存实现。</p>
 *
 * @param <ID> 聚合根主键类型
 * @author BlueWhale
 */
public abstract class AbstractAggregate<ID> implements Aggregate<ID> {

    /** 聚合根主键；新建且尚未落库时允许为 {@code null} */
    private final ID id;

    /** 待发布领域事件 */
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 构造器：绑定聚合根主键
     *
     * @param id 聚合根主键；新建且尚未落库时可传 {@code null}
     */
    protected AbstractAggregate(ID id) {
        this.id = id;
    }

    @Override
    public ID getId() {
        return id;
    }

    @Override
    public Collection<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    @Override
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    /**
     * 登记一条领域事件（仅供聚合内部业务方法调用）
     *
     * @param event 领域事件
     */
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(Objects.requireNonNull(event, "领域事件不能为空"));
    }

    /**
     * 发布并清空当前聚合收集的领域事件
     *
     * <p>先复制事件快照再清空，保证发布过程中新增的事件不会被本次发布吞掉。</p>
     *
     * @param publisher 领域事件发布器
     */
    public void publishEvents(DomainEventPublisher publisher) {
        Objects.requireNonNull(publisher, "领域事件发布器不能为空");
        Collection<DomainEvent> events = new ArrayList<>(domainEvents);
        clearDomainEvents();
        publisher.publishAll(events);
    }
}
