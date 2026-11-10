package com.bluewhale.common.domain;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.event.AbstractDomainEvent;
import com.bluewhale.common.domain.event.DomainEvent;
import com.bluewhale.common.domain.event.publisher.DomainEventPublisher;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.AbstractAggregate;
import com.bluewhale.common.domain.valueobject.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 领域内核（标识 / 聚合根 / 领域事件 / 领域异常）单元测试。
 *
 * @author BlueWhale
 */
class DomainKernelTest {

    /** 测试用标识值对象 */
    private static final class UserId extends Identifier<Long> {
        private UserId(Long value) {
            super(value);
        }
    }

    /** 测试用领域事件 */
    private static final class OrderPlacedEvent extends AbstractDomainEvent {
    }

    /** 测试用聚合根 */
    private static final class Order extends AbstractAggregate<Long> {
        private Order(Long id) {
            super(id);
        }

        private void place() {
            registerEvent(new OrderPlacedEvent());
        }
    }

    @Test
    @DisplayName("Identifier：包装取值，null 构造抛领域异常")
    void shouldWrapValueAndRejectNull() {
        UserId userId = new UserId(42L);

        assertEquals(42L, userId.getValue());
        DomainException ex = assertThrows(DomainException.class, () -> new UserId(null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("Identifier：同类型同值相等，跨类型或异值不等")
    void shouldCompareByIdentityClassAndValue() {
        UserId a = new UserId(1L);
        UserId b = new UserId(1L);
        UserId c = new UserId(2L);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        assertNotEquals(a, "1");
        assertEquals("UserId(1)", a.toString());
    }

    @Test
    @DisplayName("AbstractAggregate：登记事件后可读、发布后清空，快照在清空前复制")
    void shouldRegisterPublishAndClearEvents() {
        Order order = new Order(1L);
        assertEquals(0, order.getDomainEvents().size());
        assertNull(new Order(null).getId());

        order.place();
        assertEquals(1, order.getDomainEvents().size());

        List<DomainEvent> published = new ArrayList<>();
        DomainEventPublisher publisher = published::add;
        order.publishEvents(publisher);

        assertEquals(1, published.size());
        assertEquals(0, order.getDomainEvents().size());
    }

    @Test
    @DisplayName("AbstractAggregate：publishEvents 按快照发布（发布期间新增事件留给下一次），null 发布器拒绝")
    void shouldPublishSnapshotAndRejectNullPublisher() {
        Order order = new Order(9L);
        order.place();
        assertNotNull(order.getId());

        List<DomainEvent> published = new ArrayList<>();
        DomainEventPublisher publisher = event -> {
            published.add(event);
            order.place();
        };
        order.publishEvents(publisher);

        assertEquals(1, published.size());
        assertEquals(1, order.getDomainEvents().size());

        Order fresh = new Order(1L);
        assertThrows(NullPointerException.class, () -> fresh.publishEvents(null));
    }

    @Test
    @DisplayName("AbstractDomainEvent：事件 ID、时间与类型自动填充")
    void shouldFillEventMetadata() {
        OrderPlacedEvent event = new OrderPlacedEvent();

        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredOn());
        assertEquals("OrderPlacedEvent", event.getEventType());
        assertTrue(event.getEventId() != event2().getEventId());
    }

    @Test
    @DisplayName("DomainEventPublisher：publishAll 对 null / 空集合安全")
    void shouldTolerateNullAndEmptyBatch() {
        List<DomainEvent> published = new ArrayList<>();
        DomainEventPublisher publisher = published::add;

        publisher.publishAll(null);
        publisher.publishAll(List.of());

        assertEquals(0, published.size());
    }

    @Test
    @DisplayName("DomainException：继承 BizException，码与文案透传")
    void shouldExtendBizException() {
        DomainException ex = new DomainException(ErrorCode.FORBIDDEN, "越权");

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("越权", ex.getMessage());
    }

    /**
     * 构造另一条事件用于断言 ID 唯一性。
     *
     * @return 新事件
     */
    private static OrderPlacedEvent event2() {
        return new OrderPlacedEvent();
    }
}
