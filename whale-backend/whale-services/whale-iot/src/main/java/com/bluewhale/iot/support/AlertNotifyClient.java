package com.bluewhale.iot.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.iot.config.IotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警动作客户端：调用 whale-alert 内网接口落告警记录。
 *
 * <p>只用内网 {@code /alert/internal/record}（网关对 {@code /internal/**} 统一 403），
 * 请求经 {@link InternalTokenSupport} 自动附带 {@code X-Internal-Token}，并由
 * {@code X-Tenant-Id} 透传租户，保证告警落在设备所属租户下。</p>
 *
 * <p><b>失败策略</b>：任何异常只记日志并返回 {@code false}——规则动作是遥测链路的旁路，
 * 告警系统不可用不能反过来影响数据落库。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifyClient {

    /** 告警记录创建路径（whale-alert 内网接口） */
    private static final String RECORD_PATH = "/alert/internal/record";

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final IotProperties properties;

    /**
     * 创建一条告警记录。
     *
     * @param ruleCode    规则编码（{@code iot:rule:{id}}，便于告警侧按来源检索）
     * @param level       告警级别（INFO/WARN/ERROR/CRITICAL）
     * @param content     告警内容
     * @param fingerprint 指纹（同规则同设备同属性恒定，供告警侧窗口去重）
     * @param tenantId    设备所属租户ID
     * @param triggerTime 触发时间
     * @return true 告警侧受理；false 未配置、调用失败或业务码非 0
     */
    public boolean createRecord(String ruleCode, String level, String content,
                                String fingerprint, Long tenantId, LocalDateTime triggerTime) {
        String baseUrl = properties.getAlert().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("告警动作地址未配置，跳过规则告警：ruleCode={}", ruleCode);
            return false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "iot");
        body.put("ruleCode", ruleCode);
        body.put("alertLevel", level);
        body.put("content", content);
        body.put("fingerprint", fingerprint);
        body.put("tenantId", tenantId);
        body.put("triggerTime", triggerTime == null ? null : TIME_FORMAT.format(triggerTime));
        try {
            Map<String, Object> response = InternalTokenSupport.restClient()
                    .post()
                    .uri(baseUrl + RECORD_PATH)
                    .header(WebHeaders.TENANT_ID, String.valueOf(tenantId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                log.warn("告警接口返回空响应：ruleCode={}", ruleCode);
                return false;
            }
            Object code = response.get("code");
            boolean success = code == null || String.valueOf(CODE_SUCCESS).equals(String.valueOf(code));
            if (!success) {
                log.warn("告警接口返回业务失败：ruleCode={}, code={}, message={}",
                        ruleCode, code, response.get("message"));
            }
            return success;
        } catch (RuntimeException e) {
            log.warn("告警接口调用失败（忽略，不影响遥测）：ruleCode={}, reason={}", ruleCode, e.getMessage());
            return false;
        }
    }
}
