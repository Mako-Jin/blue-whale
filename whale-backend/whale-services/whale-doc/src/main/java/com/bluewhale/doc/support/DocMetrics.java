package com.bluewhale.doc.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 文档服务业务指标（M5 上线硬化）。
 *
 * <p>统一暴露保存冲突、搜索时延、回收站清理与孤儿附件清理指标，Prometheus 侧
 * 指标名分别为 {@code doc_page_save_conflict_total}、{@code doc_search_seconds}、
 * {@code doc_recycle_purged_total} 与 {@code doc_orphan_attachment_removed_total}。</p>
 *
 * @author BlueWhale
 */
@Component
public class DocMetrics {

    /** 页面保存版本冲突计数器 */
    private final Counter saveConflict;

    /** 回收站到期物理清除计数器 */
    private final Counter recyclePurged;

    /** 孤儿附件引用清理计数器 */
    private final Counter orphanRemoved;

    /** 孤儿附件清理失败计数器 */
    private final Counter orphanFailed;

    /** ngram 全文索引检索时延 */
    private final Timer fulltextSearch;

    /** LIKE 回退检索时延 */
    private final Timer likeSearch;

    /**
     * 注册业务指标。
     *
     * @param registry Micrometer 注册中心
     */
    public DocMetrics(MeterRegistry registry) {
        saveConflict = Counter.builder("doc.page.save.conflict")
                .description("页面保存因 baseRevision 过期产生的版本冲突次数")
                .register(registry);
        recyclePurged = Counter.builder("doc.recycle.purged")
                .description("回收站到期任务物理清除的页面子树数量")
                .register(registry);
        orphanRemoved = Counter.builder("doc.orphan.attachment.removed")
                .description("清理任务删除的无主页面附件引用数量")
                .register(registry);
        orphanFailed = Counter.builder("doc.orphan.attachment.failed")
                .description("清理任务删除附件引用失败的批次数")
                .register(registry);
        fulltextSearch = Timer.builder("doc.search")
                .description("文档搜索耗时（ngram 全文索引）")
                .tag("mode", "fulltext")
                .publishPercentileHistogram()
                .register(registry);
        likeSearch = Timer.builder("doc.search")
                .description("文档搜索耗时（短关键词 LIKE 回退）")
                .tag("mode", "like")
                .publishPercentileHistogram()
                .register(registry);
    }

    /**
     * 记录一次页面保存版本冲突。
     */
    public void recordSaveConflict() {
        saveConflict.increment();
    }

    /**
     * 记录回收站物理清除数量。
     *
     * @param count 清除的页面子树数量
     */
    public void recordRecyclePurged(int count) {
        if (count > 0) {
            recyclePurged.increment(count);
        }
    }

    /**
     * 记录孤儿附件引用清理数量。
     *
     * @param count 删除的附件引用行数
     */
    public void recordOrphanRemoved(int count) {
        if (count > 0) {
            orphanRemoved.increment(count);
        }
    }

    /**
     * 记录一次孤儿附件清理失败批次。
     */
    public void recordOrphanFailed() {
        orphanFailed.increment();
    }

    /**
     * 记录一次搜索耗时。
     *
     * @param fulltext 是否命中全文索引
     * @param nanos    耗时（纳秒）
     */
    public void recordSearch(boolean fulltext, long nanos) {
        (fulltext ? fulltextSearch : likeSearch).record(nanos, TimeUnit.NANOSECONDS);
    }
}
