package com.bluewhale.party.integration.infrastructure.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.integration.domain.gateway.ErpEmployeeGateway;
import com.bluewhale.party.integration.domain.model.ErpDirectoryPage;
import com.bluewhale.party.integration.domain.model.ErpEmployeeEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * ERP 员工目录只读网关实现。
 *
 * <p>调用 {@code GET /erp/internal/employee/directory}。异常一律转成
 * {@code EMPLOYEE_SYNC_FAILED} 上抛，不做「拉不到就当空目录」的降级——
 * 把网络故障当成空目录会让覆盖率分母归零。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpEmployeeGatewayImpl implements ErpEmployeeGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** ERP 服务名 */
    private static final String SERVICE_NAME = "whale-erp";

    private final DiscoveryClient discoveryClient;

    @Override
    public ErpDirectoryPage fetchPage(long current, long size) {
        String uri = UriComponentsBuilder
                .fromUriString(baseUrl() + "/erp/internal/employee/directory")
                .queryParam("current", Math.max(current, 1L))
                .queryParam("size", Math.max(size, 1L))
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
            log.warn("员工目录拉取失败: error={}", ex.getMessage());
            throw PartyErrorCode.exception(PartyErrorCode.EMPLOYEE_SYNC_FAILED,
                    "ERP 员工目录不可用：" + ex.getMessage());
        }
    }

    /**
     * 解析响应信封。
     *
     * @param response 响应
     * @return 分页结果
     */
    @SuppressWarnings("unchecked")
    private ErpDirectoryPage parse(Map<?, ?> response) {
        if (response == null) {
            throw PartyErrorCode.exception(PartyErrorCode.EMPLOYEE_SYNC_FAILED, "ERP 员工目录响应为空");
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            throw PartyErrorCode.exception(PartyErrorCode.EMPLOYEE_SYNC_FAILED,
                    "ERP 员工目录返回失败：" + response.get("message"));
        }
        ErpDirectoryPage page = new ErpDirectoryPage();
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> payload)) {
            return page;
        }
        page.setTotal(payload.get("total") instanceof Number total ? total.longValue() : 0L);
        Object records = payload.get("records");
        if (records instanceof List<?> list) {
            page.setRecords(list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> toEntry((Map<String, Object>) item))
                    .filter(entry -> StringUtils.hasText(entry.getEmployeeNo()))
                    .toList());
        }
        return page;
    }

    /**
     * 单行转为目录条目。
     *
     * @param row 原始行
     * @return 目录条目
     */
    private ErpEmployeeEntry toEntry(Map<String, Object> row) {
        ErpEmployeeEntry entry = new ErpEmployeeEntry();
        entry.setEmployeeNo(text(row.get("employeeNo")));
        entry.setUserId(number(row.get("userId")));
        entry.setName(text(row.get("name")));
        entry.setOrgCode(text(row.get("orgCode")));
        entry.setPosition(text(row.get("position")));
        entry.setStatus(text(row.get("status")));
        entry.setHireDate(date(row.get("hireDate")));
        entry.setLeaveDate(date(row.get("leaveDate")));
        return entry;
    }

    /**
     * 转字符串。
     *
     * @param value 原始值
     * @return 字符串
     */
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 转长整型。
     *
     * @param value 原始值
     * @return 数值
     */
    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * 转日期（脏值按空处理，不因单行坏字段中断整批同步）。
     *
     * @param value 原始值
     * @return 日期
     */
    private LocalDate date(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            log.warn("员工目录日期格式非法，按空处理: value={}", text);
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
     * 解析 ERP 服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.party.integration.e-r-p-service-unavailable", "ERP 服务不可用");
        }
        return instances.getFirst().getUri().toString();
    }
}
