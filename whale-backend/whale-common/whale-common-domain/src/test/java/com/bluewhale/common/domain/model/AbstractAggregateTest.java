package com.bluewhale.common.domain.model;

import com.bluewhale.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 聚合根领域事件收集与发布语义测试。
 */
class AbstractAggregateTest {

    @Test
    void startsEmptyAndExposesUnmodifiableEvents() {
        TestAggregate aggregate = new TestAggregate(1L);
        assertEquals(1L, aggregate.getId());
        assertTrue(aggregate.getDomainEvents().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> aggregate.getDomainEvents().add(new TestEvent("direct")));
    }

    @Test
    void registersAndClearsEvents() {
        TestAggregate aggregate = new TestAggregate(1L);
        aggregate.addEvent(new TestEvent("created"));
        assertEquals(1, aggregate.getDomainEvents().size());
        aggregate.clearDomainEvents();
        assertTrue(aggregate.getDomainEvents().isEmpty());
    }

    @Test
    void publishesSnapshotAndClearsBeforeDispatch() {
        TestAggregate aggregate = new TestAggregate(1L);
        aggregate.addEvent(new TestEvent("first"));
        List<String> published = new ArrayList<>();
        aggregate.publishEvents(event -> {
            published.add(((TestEvent) event).name());
            aggregate.addEvent(new TestEvent("during-publish"));
        });
        assertEquals(List.of("first"), published);
        assertEquals(1, aggregate.getDomainEvents().size());
    }

    @Test
    void rejectsNullEventAndPublisher() {
        TestAggregate aggregate = new TestAggregate(1L);
        assertThrows(NullPointerException.class, () -> aggregate.addEvent(null));
        assertThrows(NullPointerException.class, () -> aggregate.publishEvents(null));
    }

    /**
     * 领域事件测试替身。
     */
    private record TestEvent(String name) implements DomainEvent {
    }

    /**
     * 聚合根测试替身：暴露事件登记入口。
     */
    private static final class TestAggregate extends AbstractAggregate<Long> {

        /**
         * 构造器。
         *
         * @param id 聚合主键
         */
        private TestAggregate(Long id) {
            super(id);
        }

        /**
         * 登记领域事件。
         *
         * @param event 领域事件
         */
        private void addEvent(DomainEvent event) {
            registerEvent(event);
        }
    }
}
