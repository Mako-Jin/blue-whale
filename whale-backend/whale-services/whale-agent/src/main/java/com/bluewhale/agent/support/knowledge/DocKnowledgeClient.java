package com.bluewhale.agent.support.knowledge;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档服务内网客户端（文档知识采集用）。
 *
 * <p>仅调用 {@code /doc/internal/page/**}，服务间直连、不经网关；统一响应体仅
 * {@code code=0} 视为成功。变更流包含草稿/撤销发布/归档/删除/空间转私有墓碑，
 * 由 {@link RemoteDocChange#collectable()} 判定是否收录，采集方据此下线索引。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class DocKnowledgeClient {

    /** 变更流路径 */
    private static final String CHANGES_PATH = "/doc/internal/page/changes";

    /** 页面快照路径模板 */
    private static final String DETAIL_PATH = "/doc/internal/page/{id}";

    /** 兼容的时间格式（服务可能按空格分隔输出） */
    private static final DateTimeFormatter SPACE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 租户请求头 */
    private static final String HEADER_TENANT = "X-Tenant-Id";

    /** 响应字段：状态码 */
    private static final String FIELD_CODE = "code";

    /** 响应字段：消息 */
    private static final String FIELD_MESSAGE = "message";

    /** 响应字段：数据 */
    private static final String FIELD_DATA = "data";

    /** 响应字段：分页记录 */
    private static final String FIELD_RECORDS = "records";

    private final RestClient restClient;

    /**
     * 按采集配置构建客户端。
     *
     * @param properties Agent 配置
     */
    public DocKnowledgeClient(AgentProperties properties) {
        AgentProperties.Knowledge knowledge = properties.getKnowledge();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(knowledge.getConnectTimeoutMillis());
        factory.setReadTimeout(knowledge.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(knowledge.getDocUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 查询一页文档变更。
     *
     * @param since   游标（含，文档服务按 {@code >=} 过滤）；{@code null} 表示全量
     * @param current 页码（1 起）
     * @param size    每页条数
     * @return 变更记录（按更新时间升序）
     */
    public List<RemoteDocChange> changes(LocalDateTime since, long current, long size) {
        Map<String, Object> response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(CHANGES_PATH).queryParam("current", current).queryParam("size", size);
                    if (since != null) {
                        uriBuilder.queryParam("since", since.toString());
                    }
                    return uriBuilder.build();
                })
                .header(HEADER_TENANT, tenantHeader())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        Object data = requireData(response, "查询文档变更流");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return List.of();
        }
        Object records = dataMap.get(FIELD_RECORDS);
        if (!(records instanceof List<?> list)) {
            return List.of();
        }
        List<RemoteDocChange> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(toDoc(map));
            }
        }
        return result;
    }

    /**
     * 读取页面正文快照。
     *
     * @param pageId 页面ID
     * @return 快照；页面不可见/不存在返回 {@code null}
     */
    public RemoteDocChange detail(Long pageId) {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(DETAIL_PATH, pageId)
                    .header(HEADER_TENANT, tenantHeader())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
        Object data = requireData(response, "读取文档页面快照");
        return data instanceof Map<?, ?> map ? toDoc(map) : null;
    }

    /**
     * 取当前租户请求头；缺失租户上下文时失败关闭，避免跨租户拉取文档。
     *
     * @return 租户ID字符串
     */
    private String tenantHeader() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("缺少租户上下文，无法调用文档服务");
        }
        return String.valueOf(tenantId);
    }

    /**
     * 校验统一响应体并取出 {@code data}。
     *
     * @param response 响应体
     * @param action   动作描述
     * @return {@code data} 字段
     */
    private Object requireData(Map<String, Object> response, String action) {
        if (response == null) {
            throw new IllegalStateException(action + "失败: 空响应");
        }
        Object code = response.get(FIELD_CODE);
        if (!(code instanceof Number number) || number.intValue() != 0) {
            throw new IllegalStateException(action + "失败: " + response.get(FIELD_MESSAGE));
        }
        return response.get(FIELD_DATA);
    }

    /**
     * 把响应的键值映射转换为页面变更记录。
     *
     * @param map 响应数据
     * @return 变更记录
     */
    private RemoteDocChange toDoc(Map<?, ?> map) {
        RemoteDocChange change = new RemoteDocChange();
        change.setPageId(longValue(map.get("pageId")));
        change.setSpaceId(longValue(map.get("spaceId")));
        change.setSpaceName(stringValue(map.get("spaceName")));
        change.setTitle(stringValue(map.get("title")));
        change.setSlug(stringValue(map.get("slug")));
        change.setStatus(stringValue(map.get("status")));
        change.setRevision(longValue(map.get("revision")));
        change.setRestricted(booleanValue(map.get("restricted")));
        change.setDeleted(booleanValue(map.get("deleted")));
        change.setSpaceVisibility(stringValue(map.get("spaceVisibility")));
        change.setSpaceActive(booleanValue(map.get("spaceActive")));
        change.setContentText(stringValue(map.get("contentText")));
        change.setUpdateTime(timeValue(map.get("updateTime")));
        return change;
    }

    /**
     * 解析长整型（兼容数字与数字字符串）。
     *
     * @param value 原值
     * @return 长整型；无法解析返回 {@code null}
     */
    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 解析布尔值（兼容布尔与字符串）。
     *
     * @param value 原值
     * @return 布尔值；无法解析返回 {@code null}
     */
    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            String text = String.valueOf(value).trim();
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /**
     * 解析字符串。
     *
     * @param value 原值
     * @return 字符串；空值返回 {@code null}
     */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 解析时间（兼容 ISO-8601 与空格分隔格式）。
     *
     * @param value 原值
     * @return 时间；无法解析返回 {@code null}
     */
    private LocalDateTime timeValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text, SPACE_FORMAT);
            } catch (DateTimeParseException e) {
                log.warn("无法解析文档更新时间: {}", text);
                return null;
            }
        }
    }
}
