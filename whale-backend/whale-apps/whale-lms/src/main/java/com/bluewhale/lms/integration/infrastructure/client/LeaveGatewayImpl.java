package com.bluewhale.lms.integration.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.lms.integration.domain.gateway.LeaveGateway;
import com.bluewhale.lms.integration.domain.model.LeaveConflictEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OA 请假只读网关实现。
 *
 * <p>调用 OA 内网契约 {@code GET /oa/internal/leave/approved}。</p>
 *
 * <p>与员工目录网关相反，这里的失败<b>降级为空列表</b>而不是上抛：冲突提示是辅助信息，
 * OA 短暂不可达不应该让排期页面整体打不开。代价是「查不到」与「没有请假」看起来一样，
 * 因此失败会打 warn 日志并交由调用方在视图上标注「请假信息暂不可用」。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveGatewayImpl implements LeaveGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** OA 服务名 */
    private static final String SERVICE_NAME = "whale-oa";

    private final DiscoveryClient discoveryClient;

    @Override
    public List<LeaveConflictEntry> listApproved(Collection<Long> userIds, LocalDate from, LocalDate to) {
        if (userIds == null || userIds.isEmpty() || from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        String joined = userIds.stream().filter(id -> id != null && id > 0)
                .distinct().map(String::valueOf).collect(Collectors.joining(","));
        if (joined.isEmpty()) {
            return List.of();
        }
        String uri = UriComponentsBuilder.fromUriString(baseUrl() + "/oa/internal/leave/approved")
                .queryParam("userIds", joined)
                .queryParam("from", from.toString())
                .queryParam("to", to.toString())
                .build().encode().toUri().toString();
        try {
            Map<?, ?> response = InternalTokenSupport.restClient()
                    .get()
                    .uri(uri)
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId()))
                    .retrieve()
                    .body(Map.class);
            return parse(response);
        } catch (RestClientResponseException | ResourceAccessException ex) {
            log.warn("请假冲突查询失败（按无请假处理）: error={}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * 解析响应信封。
     *
     * @param response 响应
     * @return 请假列表
     */
    @SuppressWarnings("unchecked")
    private List<LeaveConflictEntry> parse(Map<?, ?> response) {
        if (response == null) {
            return List.of();
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            log.warn("请假冲突返回失败: message={}", response.get("message"));
            return List.of();
        }
        Object data = response.get("data");
        if (!(data instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> toEntry((Map<String, Object>) item))
                .filter(entry -> entry.getApplicantId() != null)
                .toList();
    }

    /**
     * 单行转为领域条目。
     *
     * @param row 原始行
     * @return 条目
     */
    private LeaveConflictEntry toEntry(Map<String, Object> row) {
        LeaveConflictEntry entry = new LeaveConflictEntry();
        entry.setCode(text(row.get("code")));
        entry.setApplicantId(row.get("applicantId") instanceof Number number ? number.longValue() : null);
        entry.setApplicant(text(row.get("applicant")));
        entry.setLeaveType(text(row.get("leaveType")));
        entry.setStartDate(date(row.get("startDate")));
        entry.setEndDate(date(row.get("endDate")));
        entry.setStatus(text(row.get("status")));
        return entry;
    }

    /**
     * 转字符串。
     *
     * @param value 原始值
     * @return 字符串
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 转日期。
     *
     * @param value 原始值
     * @return 日期；非法返回 {@code null}
     */
    private LocalDate date(Object value) {
        String text = text(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            log.warn("请假日期格式非法: value={}", text);
            return null;
        }
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
     * 解析 OA 服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.lms.integration.o-service-unavailable", "OA 服务不可用");
        }
        return instances.getFirst().getUri().toString();
    }
}
