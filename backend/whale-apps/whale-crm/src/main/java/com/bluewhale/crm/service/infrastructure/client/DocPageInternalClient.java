package com.bluewhale.crm.service.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.crm.config.CrmDocsProperties;
import com.bluewhale.crm.service.domain.port.DocPagePort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * whale-doc 内网文档详情客户端。
 *
 * <p>仅读取公开已发布文档元数据（标题 / 版本），正文不复制；服务不可用时降级为空。</p>
 *
 * @author BlueWhale
 */
@Component
public class DocPageInternalClient implements DocPagePort {

    /** 文档详情路径 */
    private static final String PAGE_PATH = "/doc/internal/page/{id}";

    /** 统一响应体状态码字段 */
    private static final String FIELD_CODE = "code";

    /** 统一响应体数据字段 */
    private static final String FIELD_DATA = "data";

    /** 成功状态码 */
    private static final int CODE_SUCCESS = 0;

    /** 默认租户 ID */
    private static final long DEFAULT_TENANT_ID = 1L;

    /** 文档配置 */
    private final CrmDocsProperties properties;

    /** 内网客户端 */
    private final RestClient restClient;

    /**
     * 按文档配置构建客户端。
     *
     * @param properties 文档配置
     */
    public DocPageInternalClient(CrmDocsProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(properties.getDocUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<DocPageSnapshot> findPublicPage(Long docId) {
        if (!properties.isEnabled() || docId == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> response = restClient.get()
                    .uri(PAGE_PATH, docId)
                    .header(WebHeaders.TENANT_ID, tenantHeader())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                return Optional.empty();
            }
            Object code = response.get(FIELD_CODE);
            if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
                return Optional.empty();
            }
            Object data = response.get(FIELD_DATA);
            if (!(data instanceof Map<?, ?> map)) {
                return Optional.empty();
            }
            Map<String, Object> node = (Map<String, Object>) map;
            String title = text(node.get("title"));
            Integer revision = integer(node.get("revision"));
            return Optional.of(new DocPageSnapshot(title, revision));
        } catch (RuntimeException exception) {
            // 降级：文档服务不可用不阻断知识引用保存，只写 doc_available=false
            return Optional.empty();
        }
    }

    /**
     * 当前租户头值。
     *
     * @return 租户 ID 文本
     */
    private String tenantHeader() {
        Long tenantId = TenantContext.get();
        return String.valueOf(tenantId == null ? DEFAULT_TENANT_ID : tenantId);
    }

    /**
     * 文本转换。
     *
     * @param value 原始值
     * @return 文本；空返回 null
     */
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 整型转换。
     *
     * @param value 原始值
     * @return 整型；无法解析返回 null
     */
    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
