package com.bluewhale.forum.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 论坛业务指标。
 *
 * <p>只登记「HTTP 指标看不出来」的那部分：计数漂移是数据正确性问题，
 * 接口全 200 也可能一直在漂，必须有自己的计数器才能告警。检索与列表的时延
 * 直接看 {@code http_server_requests_seconds} 即可，不重复造计时器。</p>
 *
 * <p>Prometheus 指标名：{@code forum_count_reconcile_diff_total}（按 {@code metric} 分标签）。</p>
 *
 * @author BlueWhale
 */
@Component
public class ForumMetrics {

    private final MeterRegistry registry;

    /**
     * 注册业务指标。
     *
     * @param registry Micrometer 注册中心
     */
    public ForumMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录一条计数差异（同名指标不同 metric 标签，Micrometer 会按标签拆成多条时间序列）。
     *
     * @param metric 计数项（REPLY / LIKE / COLLECT）
     */
    public void recordReconcileDiff(String metric) {
        Counter.builder("forum.count.reconcile.diff")
                .description("计数对账发现的展示值与明细差异条数")
                .tag("metric", metric)
                .register(registry)
                .increment();
    }
}
