package com.bluewhale.file.metrics;

import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 文件服务业务指标。
 *
 * <p>暴露上传、下载、回收站恢复、配额锁等待与存储插件状态；Prometheus 侧指标名分别为
 * {@code file_upload_total}、{@code file_upload_size_bytes}、{@code file_upload_duration_seconds}、
 * {@code file_download_total}、{@code file_download_size_bytes}、{@code file_download_duration_seconds}、
 * {@code file_recycle_restore_total}、{@code file_quota_lock_seconds}、
 * {@code file_chunk_session_active} 与 {@code file_storage_plugin_active}。</p>
 *
 * <p>标签只使用低基数值（上传模式、结果、存储类型），不放 bucket / contentType / 文件名，
 * 避免 Prometheus 时间序列膨胀。</p>
 *
 * @author BlueWhale
 */
@Component
public class FileMetrics {

    /** 上传指标名 */
    private static final String UPLOAD = "file.upload";

    /** 下载指标名 */
    private static final String DOWNLOAD = "file.download";

    /** 回收站恢复指标名 */
    private static final String RESTORE = "file.recycle.restore";

    private final MeterRegistry registry;

    /**
     * 注册业务指标与状态直读 Gauge。
     *
     * @param registry Micrometer 注册中心
     * @param chunkUploadStore 分片暂存端口（活跃会话数）
     * @param storagePluginCatalog 存储插件目录（活动插件）
     */
    public FileMetrics(MeterRegistry registry, ChunkUploadStore chunkUploadStore,
                       StoragePluginCatalog storagePluginCatalog) {
        this.registry = registry;
        Gauge.builder("file.chunk.session.active", chunkUploadStore, ChunkUploadStore::sessionCount)
                .description("分片上传暂存目录中的活跃会话数量")
                .register(registry);
        for (StoragePluginInfo plugin : storagePluginCatalog.plugins()) {
            String type = plugin.type();
            Gauge.builder("file.storage.plugin.active", () -> type.equals(storagePluginCatalog.activeType()) ? 1d : 0d)
                    .description("存储插件是否为当前活动插件（1 活动 / 0 非活动）")
                    .tag("type", type)
                    .register(registry);
        }
    }

    /**
     * 记录一次上传。
     *
     * @param mode 上传模式：multipart / chunk / rapid
     * @param bytes 文件字节数
     * @param nanos 耗时（纳秒）
     * @param result 结果：success / failure / miss
     */
    public void recordUpload(String mode, long bytes, long nanos, String result) {
        Counter.builder(UPLOAD)
                .description("文件上传次数")
                .tag("mode", mode)
                .tag("result", result)
                .register(registry)
                .increment();
        if ("miss".equals(result)) {
            // 秒传未命中没有实际上传动作，不记录耗时与字节数，避免污染分布
            return;
        }
        Timer.builder(UPLOAD + ".duration")
                .description("文件上传耗时")
                .tag("mode", mode)
                .publishPercentileHistogram()
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
        if ("success".equals(result)) {
            DistributionSummary.builder(UPLOAD + ".size")
                    .description("文件上传字节数")
                    .baseUnit("bytes")
                    .tag("mode", mode)
                    .register(registry)
                    .record(bytes);
        }
    }

    /**
     * 记录一次下载/预览内容读取。
     *
     * @param bytes 文件字节数
     * @param nanos 打开内容流耗时（纳秒）
     */
    public void recordDownload(long bytes, long nanos) {
        Counter.builder(DOWNLOAD)
                .description("文件下载/预览次数")
                .register(registry)
                .increment();
        Timer.builder(DOWNLOAD + ".duration")
                .description("文件内容流打开耗时")
                .publishPercentileHistogram()
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
        DistributionSummary.builder(DOWNLOAD + ".size")
                .description("文件下载字节数")
                .baseUnit("bytes")
                .register(registry)
                .record(bytes);
    }

    /**
     * 记录一次回收站恢复。
     *
     * @param success 是否成功（含幂等已恢复）
     */
    public void recordRestore(boolean success) {
        Counter.builder(RESTORE)
                .description("回收站文件恢复次数")
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
    }
}
