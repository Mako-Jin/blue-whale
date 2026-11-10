package com.bluewhale.mes.common.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.mes.common.outbox.entity.MesOutboxEvent;
import com.bluewhale.mes.common.outbox.mapper.MesOutboxEventMapper;
import com.bluewhale.mes.common.outbox.service.MesOutboxService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MES 发件箱定时投递器
 *
 * <p>扫描 {@code PENDING} 与到期 {@code FAILED} 事件，调用 ERP 完工回传接口
 * {@code POST /erp/internal/production-order/{erpOrderNo}/completion}。失败按 1/5/15/60 分钟
 * 指数退避；默认关闭（{@code whale.mes.outbox.enabled=false}），未配置 ERP 地址时保持待投递，
 * 不影响车间现场闭环。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class MesOutboxDispatcher {

    /** 待投递状态 */
    private static final String STATUS_PENDING = "PENDING";

    /** 投递失败状态 */
    private static final String STATUS_FAILED = "FAILED";

    /** 完成事件类型 */
    private static final String EVENT_COMPLETED = "WorkOrderCompleted";

    /** 进度事件类型 */
    private static final String EVENT_PROGRESS = "WorkOrderProgress";

    /** 缺省租户 */
    private static final String DEFAULT_TENANT_ID = "1";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MesOutboxEventMapper outboxEventMapper;

    private final MesOutboxService outboxService;

    private final RestClient restClient = InternalTokenSupport.builder().build();

    /** 是否启用定时投递 */
    private final boolean enabled;

    /** ERP 服务基地址，为空时不投递 */
    private final String erpBaseUrl;

    /** 单批投递数量 */
    private final int batchSize;

    /**
     * 构造投递器
     *
     * @param outboxEventMapper 发件箱 Mapper
     * @param outboxService     发件箱服务
     * @param enabled           是否启用
     * @param erpBaseUrl        ERP 基地址
     * @param batchSize         单批数量
     */
    public MesOutboxDispatcher(MesOutboxEventMapper outboxEventMapper,
                               MesOutboxService outboxService,
                               @Value("${whale.mes.outbox.enabled:false}") boolean enabled,
                               @Value("${whale.mes.integration.erp-base-url:}") String erpBaseUrl,
                               @Value("${whale.mes.outbox.batch-size:20}") int batchSize) {
        this.outboxEventMapper = outboxEventMapper;
        this.outboxService = outboxService;
        this.enabled = enabled;
        this.erpBaseUrl = trimTrailingSlash(erpBaseUrl);
        this.batchSize = batchSize;
    }

    /**
     * 定时投递待发送事件
     */
    @Scheduled(fixedDelayString = "${whale.mes.outbox.interval-ms:30000}")
    public void dispatchPending() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(erpBaseUrl)) {
            log.debug("MES 发件箱投递跳过：未配置 ERP 地址");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<MesOutboxEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(condition -> condition.eq(MesOutboxEvent::getStatus, STATUS_PENDING)
                .or(failed -> failed.eq(MesOutboxEvent::getStatus, STATUS_FAILED)
                        .le(MesOutboxEvent::getNextRetryTime, now)));
        wrapper.orderByAsc(MesOutboxEvent::getId);
        int size = Math.min(Math.max(batchSize, 1), 100);
        wrapper.last("LIMIT " + size);
        List<MesOutboxEvent> events = outboxEventMapper.selectList(wrapper);
        for (MesOutboxEvent event : events) {
            deliver(event);
        }
    }

    /**
     * 投递单条事件
     *
     * @param event 发件箱事件
     */
    private void deliver(MesOutboxEvent event) {
        String action;
        if (EVENT_COMPLETED.equals(event.getEventType())) {
            action = "completion";
        } else if (EVENT_PROGRESS.equals(event.getEventType())) {
            action = "progress";
        } else {
            log.debug("MES 发件箱跳过不支持的事件类型: {}", event.getEventType());
            return;
        }
        String erpOrderNo = readErpOrderNo(event.getPayload());
        if (!StringUtils.hasText(erpOrderNo)) {
            fail(event, "事件报文缺少 erpOrderNo");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.getSourceEventId());
        body.put("payload", event.getPayload());
        try {
            restClient.post()
                    .uri(erpBaseUrl + "/erp/internal/production-order/" + erpOrderNo + "/" + action)
                    .header(WebHeaders.TENANT_ID, readTenantId(event.getPayload()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            outboxService.markSent(event.getId());
        } catch (RuntimeException exception) {
            fail(event, exception.getMessage());
        }
    }

    /**
     * 标记失败并计算下次重试时间
     *
     * @param event 事件
     * @param error 错误摘要
     */
    private void fail(MesOutboxEvent event, String error) {
        int retryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
        int delayMinutes = Math.min(60, 1 << Math.min(retryCount, 6));
        outboxService.markFailed(event.getId(), retryCount,
                LocalDateTime.now().plusMinutes(delayMinutes), truncate(error));
        log.warn("MES 发件箱投递失败: id={}, retry={}, error={}", event.getId(), retryCount, error);
    }

    /**
     * 从事件报文读取 ERP 单号
     *
     * @param payload JSON 报文
     * @return ERP 单号，解析失败返回 {@code null}
     */
    private String readErpOrderNo(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            return root.path("erpOrderNo").asText(null);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 从事件报文读取租户 ID（后台投递无租户上下文）。
     *
     * @param payload JSON 报文
     * @return 租户 ID 字符串
     */
    private String readTenantId(String payload) {
        if (!StringUtils.hasText(payload)) {
            return DEFAULT_TENANT_ID;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            String tenantId = root.path("tenantId").asText(DEFAULT_TENANT_ID);
            return StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
        } catch (Exception exception) {
            return DEFAULT_TENANT_ID;
        }
    }

    /**
     * 截断错误摘要，避免超出列宽
     *
     * @param error 错误信息
     * @return 截断后的摘要
     */
    private String truncate(String error) {
        if (error == null) {
            return "unknown";
        }
        return error.length() > 200 ? error.substring(0, 200) : error;
    }

    /**
     * 去除末尾斜杠
     *
     * @param url 原始地址
     * @return 规整后的地址
     */
    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
