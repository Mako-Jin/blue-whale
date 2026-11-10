package com.bluewhale.erp.mfg.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.mfg.domain.model.MesProgressSnapshot;
import com.bluewhale.erp.mfg.domain.model.MesPushResult;
import com.bluewhale.erp.mfg.domain.model.ProductionOrder;
import com.bluewhale.erp.mfg.domain.port.MesWorkOrderGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MES 工单内网网关实现。
 *
 * <p>只调用网关屏蔽的 {@code /mes/internal/**}；请求携带内网令牌与租户头。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MesWorkOrderGatewayImpl implements MesWorkOrderGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** MES 服务名 */
    private static final String SERVICE_NAME = "whale-mes";

    /** 默认优先级 */
    private static final String DEFAULT_PRIORITY = "NORMAL";

    /** 默认计量单位 */
    private static final String DEFAULT_UOM = "PCS";

    private final DiscoveryClient discoveryClient;

    @Override
    public MesPushResult push(ProductionOrder order) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("erpOrderNo", order.getOrderNo());
        request.put("itemCode", order.getItemCode());
        request.put("itemName", order.getItemName());
        request.put("plannedQuantity", order.getPlannedQuantity());
        request.put("uom", DEFAULT_UOM);
        request.put("routingVersion", order.getRoutingVersion());
        request.put("priority", DEFAULT_PRIORITY);
        request.put("plannedStart", order.getPlanStartDate() == null
                ? null : order.getPlanStartDate().atStartOfDay());
        request.put("plannedEnd", order.getPlanEndDate() == null
                ? null : order.getPlanEndDate().atTime(LocalTime.MAX));
        request.put("code", order.getOrderNo());
        request.put("name", order.getItemName());
        request.put("remark", "ERP 生产订单下发");
        Map<String, Object> response = InternalTokenSupport.restClient()
                .post()
                .uri(baseUrl() + "/mes/internal/work-order")
                .header(WebHeaders.TENANT_ID, String.valueOf(tenantId()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        Object data = requireSuccess(response);
        if (data instanceof Map<?, ?> map) {
            return new MesPushResult(toLong(map.get("id")), toText(map.get("code")));
        }
        throw failure("MES 工单响应格式非法");
    }

    @Override
    public Optional<MesProgressSnapshot> pull(String erpOrderNo) {
        try {
            Map<String, Object> response = InternalTokenSupport.restClient()
                    .get()
                    .uri(baseUrl() + "/mes/internal/work-order/" + erpOrderNo)
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                throw failure("MES 响应为空");
            }
            Object code = response.get("code");
            if (code instanceof Number number && number.intValue() == 404) {
                return Optional.empty();
            }
            Object data = requireSuccess(response);
            if (!(data instanceof Map<?, ?> map)) {
                return Optional.empty();
            }
            return Optional.of(toSnapshot(map));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw failure(e.getStatusText());
        }
    }

    /**
     * 校验响应并返回 data 字段。
     *
     * @param response MES 响应
     * @return data 字段
     */
    private Object requireSuccess(Map<String, Object> response) {
        if (response == null) {
            throw failure("MES 响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw failure(String.valueOf(response.get("message")));
        }
        return response.get("data");
    }

    /**
     * 解析 MES 工单进度视图。
     *
     * @param map 响应 data
     * @return 进度快照
     */
    private MesProgressSnapshot toSnapshot(Map<?, ?> map) {
        MesProgressSnapshot snapshot = new MesProgressSnapshot();
        snapshot.setErpOrderNo(toText(map.get("erpOrderNo")));
        snapshot.setWorkOrderId(toLong(map.get("workOrderId")));
        snapshot.setWorkOrderCode(toText(map.get("workOrderCode")));
        snapshot.setMesStatus(toText(map.get("status")));
        snapshot.setPlannedQuantity(toDecimal(map.get("plannedQuantity")));
        snapshot.setReportedQuantity(toDecimal(map.get("reportedQuantity")));
        snapshot.setQualifiedQuantity(toDecimal(map.get("qualifiedQuantity")));
        snapshot.setCompletedQuantity(toDecimal(map.get("completedQuantity")));
        snapshot.setReceivedAt(java.time.LocalDateTime.now());
        if (map.get("tasks") instanceof List<?> list) {
            List<MesProgressSnapshot.Task> tasks = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> taskMap) {
                    MesProgressSnapshot.Task task = new MesProgressSnapshot.Task();
                    task.setTaskNo(toText(taskMap.get("taskNo")));
                    task.setSeqNo(toInteger(taskMap.get("seqNo")));
                    task.setOperationCode(toText(taskMap.get("operationCode")));
                    task.setPlannedQuantity(toDecimal(taskMap.get("plannedQuantity")));
                    task.setReportedQuantity(toDecimal(taskMap.get("reportedQuantity")));
                    task.setQualifiedQuantity(toDecimal(taskMap.get("qualifiedQuantity")));
                    task.setCompletedQuantity(toDecimal(taskMap.get("completedQuantity")));
                    task.setStatus(toText(taskMap.get("status")));
                    tasks.add(task);
                }
            }
            snapshot.setTasks(tasks);
        }
        return snapshot;
    }

    /**
     * 解析 MES 服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw failure("MES 服务不可用");
        }
        return instances.get(0).getUri().toString();
    }

    /**
     * 当前租户。
     *
     * @return 租户 ID
     */
    private Long tenantId() {
        Long tenantId = TenantContext.get();
        return tenantId == null ? TenantContext.DEFAULT_TENANT_ID : tenantId;
    }

    /**
     * 构造受控失败异常。
     *
     * @param message 失败原因
     * @return 业务异常
     */
    private BizException failure(String message) {
        String safe = StringUtils.hasText(message) ? message : "未知错误";
        return ErpErrorCode.exception(ErpErrorCode.MES_GATEWAY_FAILED, "MES 调用失败: " + safe);
    }

    /**
     * 转字符串。
     *
     * @param value 原值
     * @return 字符串
     */
    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 转长整型。
     *
     * @param value 原值
     * @return 长整型
     */
    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return StringUtils.hasText(toText(value)) ? Long.parseLong(String.valueOf(value)) : null;
    }

    /**
     * 转整型。
     *
     * @param value 原值
     * @return 整型
     */
    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return StringUtils.hasText(toText(value)) ? Integer.parseInt(String.valueOf(value)) : null;
    }

    /**
     * 转金额/数量。
     *
     * @param value 原值
     * @return 数值
     */
    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
