package com.bluewhale.scene.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.scene.application.port.ManufacturingBoardPort;
import com.bluewhale.scene.application.view.MfgBoardView;
import com.bluewhale.scene.config.SceneProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * 制造看板内网客户端：调用 ERP {@code /erp/internal/mfg/board}。
 *
 * <p>失败语义为 fail-open：网络、解析、业务码失败统一返回 empty 并记 WARN，
 * 由上层返回 {@code degraded=true}，前端据此显式提示，绝不补 0 或假数据。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ErpManufacturingBoardClient implements ManufacturingBoardPort {

    /** ERP 内网看板路径 */
    private static final String BOARD_PATH = "/erp/internal/mfg/board";

    /** JSON 映射器（Jackson 3，无状态复用） */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /** 场景服务配置（取 ERP 内网基地址） */
    private final SceneProperties properties;

    /** 测试注入的客户端；为空时按内网令牌现场创建 */
    private final RestClient client;

    /**
     * 生产用构造：客户端按 {@link InternalTokenSupport} 当前配置现场创建。
     *
     * @param properties 场景服务配置
     */
    @Autowired
    public ErpManufacturingBoardClient(SceneProperties properties) {
        this(properties, null);
    }

    /**
     * 测试用构造：外部传入 RestClient。
     *
     * @param properties 场景服务配置
     * @param client     调用客户端（为空表示按内网令牌现场创建）
     */
    ErpManufacturingBoardClient(SceneProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public Optional<MfgBoardView> fetch(Long tenantId, int limit) {
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        String raw;
        try {
            raw = target.get()
                    .uri(properties.getErpBaseUrl() + BOARD_PATH + "?limit=" + limit)
                    .headers(headers -> {
                        headers.set(InternalTokenSupport.headerName(), InternalTokenSupport.token());
                        headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException error) {
            log.warn("制造看板取数失败（按降级处理）：tenantId={}, reason={}",
                    tenantId, error.getMessage());
            return Optional.empty();
        }
        if (!StringUtils.hasText(raw)) {
            log.warn("制造看板响应为空（按降级处理）：tenantId={}", tenantId);
            return Optional.empty();
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(raw);
            JsonNode code = root.path("code");
            if (!code.isMissingNode() && code.asInt(-1) != 0) {
                log.warn("制造看板业务失败（按降级处理）：tenantId={}, message={}",
                        tenantId, root.path("message").asText());
                return Optional.empty();
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull() || !data.isObject()) {
                log.warn("制造看板响应缺少 data（按降级处理）：tenantId={}", tenantId);
                return Optional.empty();
            }
            return Optional.of(JSON_MAPPER.readValue(data.toString(), MfgBoardView.class));
        } catch (RuntimeException error) {
            log.warn("制造看板响应解析失败（按降级处理）：tenantId={}, reason={}",
                    tenantId, error.getMessage());
            return Optional.empty();
        }
    }
}
