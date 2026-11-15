package com.bluewhale.file.infrastructure.event;

import com.bluewhale.common.domain.event.DomainEvent;
import com.bluewhale.common.domain.event.publisher.DomainEventPublisher;
import com.bluewhale.file.domain.file.event.FileAssetDomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring 事件的领域事件发布适配器。
 *
 * <p>把应用层依赖的 {@link DomainEventPublisher} 端口桥接到 {@link ApplicationEventPublisher}，
 * 使审计、缓存失效等基础设施动作与文件生命周期解耦。</p>
 *
 * <p><b>失败语义</b>：事件是业务结果的「通知」而非业务本身，监听器异常只记录 WARN，
 * 不向上抛出——否则删除/恢复已经落库成功，却因通知失败让调用方收到错误并触发重试。
 * 需要强一致的消费方应改用事务性 outbox，而不是把异常反向传播。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    /** Spring 事件发布器 */
    private final ApplicationEventPublisher publisher;

    /**
     * 构造器注入
     *
     * @param publisher Spring 事件发布器
     */
    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException e) {
            log.warn("领域事件通知失败（不影响业务结果）: type={}, fileId={}",
                    event.getClass().getSimpleName(), fileIdOf(event), e);
        }
    }

    /**
     * 提取文件事件的文件ID用于日志定位
     *
     * @param event 领域事件
     * @return 文件ID；非文件事件或未携带时返回 {@code null}
     */
    private static Long fileIdOf(DomainEvent event) {
        return event instanceof FileAssetDomainEvent fileEvent ? fileEvent.fileId() : null;
    }
}
