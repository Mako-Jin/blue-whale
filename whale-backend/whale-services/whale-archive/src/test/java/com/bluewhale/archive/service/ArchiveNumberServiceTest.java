package com.bluewhale.archive.service;

import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.mapper.ArchiveSequenceMapper;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ArchiveNumberService} 单元测试：格式、配置化前缀宽度、参数校验与并发唯一性。
 *
 * <p>并发用例用 {@link ThreadLocal} 模拟 MySQL 连接级 {@code LAST_INSERT_ID} 语义。</p>
 *
 * @author BlueWhale
 */
class ArchiveNumberServiceTest {

    /** 并发线程数 */
    private static final int CONCURRENT_THREADS = 20;

    /** 并发等待超时（秒） */
    private static final long TIMEOUT_SECONDS = 10L;

    private final ArchiveSequenceMapper sequenceMapper = mock(ArchiveSequenceMapper.class);

    private final ArchiveProperties properties = new ArchiveProperties();

    private final ArchiveNumberService service = new ArchiveNumberService(sequenceMapper, properties);

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    private final ThreadLocal<Long> lastValue = new ThreadLocal<>();

    /**
     * 每个用例前重置流水计数，并模拟数据库的「分配 + 当前连接取值」语义。
     */
    @BeforeEach
    void stubAtomicSequence() {
        counters.clear();
        lastValue.remove();
        when(sequenceMapper.allocate(anyLong(), anyString(), anyInt())).thenAnswer(invocation -> {
            String key = invocation.getArgument(1) + ":" + invocation.getArgument(2);
            long value = counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
            lastValue.set(value);
            return 1;
        });
        when(sequenceMapper.lastAllocatedValue()).thenAnswer(invocation -> lastValue.get());
    }

    /**
     * 默认前缀与 6 位流水应输出标准档号并递增。
     */
    @Test
    void nextShouldFormatStandardArchiveNumber() {
        assertThat(service.next("GL", 2026)).isEqualTo("BW-GL-2026-000001");
        assertThat(service.next("GL", 2026)).isEqualTo("BW-GL-2026-000002");
        assertThat(service.next("CW", 2026)).isEqualTo("BW-CW-2026-000001");
    }

    /**
     * 前缀、流水宽度可配置，门类编码统一转大写。
     */
    @Test
    void nextShouldUseConfiguredPrefixAndWidth() {
        properties.getNumber().setPrefix("DA");
        properties.getNumber().setSequenceWidth(4);

        assertThat(service.next("gl", 2026)).isEqualTo("DA-GL-2026-0001");
    }

    /**
     * 年度越界应在访问数据库前拒绝。
     */
    @Test
    void nextShouldRejectInvalidYear() {
        assertThatThrownBy(() -> service.next("GL", 1899))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("归档年度");
        verify(sequenceMapper, never()).allocate(anyLong(), anyString(), anyInt());
    }

    /**
     * 门类编码含非法字符应拒绝。
     */
    @Test
    void nextShouldRejectInvalidCategoryCode() {
        assertThatThrownBy(() -> service.next("G L", 2026))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("门类编码");
        verify(sequenceMapper, never()).allocate(anyLong(), anyString(), anyInt());
    }

    /**
     * 20 线程并发取号不应重复，且完整覆盖 1..20。
     */
    @Test
    void nextShouldAllocateUniqueNumbersUnderConcurrency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENT_THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.next("GL", 2026);
                }));
            }
            start.countDown();
            Set<String> numbers = new HashSet<>();
            for (Future<String> future : futures) {
                numbers.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            assertThat(numbers).hasSize(CONCURRENT_THREADS);
            assertThat(numbers).allMatch(number -> number.matches("BW-GL-2026-\\d{6}"));
            assertThat(numbers).contains("BW-GL-2026-000001", "BW-GL-2026-000020");
        } finally {
            pool.shutdownNow();
        }
    }
}
