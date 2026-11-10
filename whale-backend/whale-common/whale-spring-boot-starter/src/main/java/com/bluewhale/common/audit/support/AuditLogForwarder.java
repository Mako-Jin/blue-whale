package com.bluewhale.common.audit.support;

import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.properties.AuditLogForwardProperties;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * 审计日志转发器：把本服务产生的审计记录推送到 whale-audit 统一库。
 *
 * <p>仅在 {@code whale.audit.forward.enabled=true} 时装配。调用语义为「尽力而为」：
 * 成功（HTTP 2xx）返回 {@code true}；超时、4xx/5xx、序列化失败等一律返回 {@code false}
 * 并告警，由调用方（{@code AuditLogAspect}）回退本地落库，<b>绝不抛出异常</b>。</p>
 *
 * <p>请求体为单元素 JSON 数组，字段与 {@code audit_log} 表一一对应；接收端会强制重置主键，
 * 由目标库重新生成雪花 ID，避免跨库主键冲突。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class AuditLogForwarder {

    /** 摄入端点路径（与 whale-audit {@code AuditLogController} 对齐） */
    private static final String INGEST_PATH = "/audit/log/ingest";

    private final RestClient restClient;

    private final ObjectMapper objectMapper;

    /**
     * 构造转发器
     *
     * @param properties 转发配置（基址与超时）
     * @param objectMapper Spring 管理的 Jackson 3 序列化器（含 JavaTime 模块）
     */
    public AuditLogForwarder(AuditLogForwardProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofMillis(properties.getTimeoutMillis());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 转发单条审计记录
     *
     * @param record 待转发的审计记录
     * @return {@code true} 表示接收端已成功落库；{@code false} 表示需回退本地落库
     */
    public boolean forward(AuditLogRecord record) {
        try {
            String body = objectMapper.writeValueAsString(List.of(record));
            ResponseEntity<Void> response = restClient.post()
                    .uri(INGEST_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("审计日志转发失败，回退本地落库: traceId={}, operation={}, reason={}",
                    record.getTraceId(), record.getOperation(), e.getMessage());
            return false;
        }
    }
}
