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
 * 文件服务内网客户端（知识库采集用）。
 *
 * <p>仅调用 {@code /file/v1/internal/**}，服务间直连、不经网关；统一响应体仅 {@code code=0}
 * 视为成功。变更流按 {@code update_time,id} 升序返回，调用方以返回记录的最大
 * {@code updateTime} 作为下一轮游标。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class FileKnowledgeClient {

    /** 变更流路径 */
    private static final String CHANGES_PATH = "/file/v1/internal/changes";

    /** 文件元数据路径模板 */
    private static final String DETAIL_PATH = "/file/v1/internal/{id}";

    /** 文件内容路径模板 */
    private static final String CONTENT_PATH = "/file/v1/internal/{id}/content";

    /** 兼容的时间格式（文件服务可能按空格分隔输出） */
    private static final DateTimeFormatter SPACE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 响应字段：状态码 */
    private static final String FIELD_CODE = "code";

    /** 响应字段：消息 */
    private static final String FIELD_MESSAGE = "message";

    /** 响应字段：数据 */
    private static final String FIELD_DATA = "data";

    /** 响应字段：分页记录 */
    private static final String FIELD_RECORDS = "records";

    /** 租户请求头（文件服务按租户隔离文件查询） */
    private static final String HEADER_TENANT = "X-Tenant-Id";

    private final RestClient restClient;

    /**
     * 按采集配置构建客户端。
     *
     * @param properties Agent 配置
     */
    public FileKnowledgeClient(AgentProperties properties) {
        AgentProperties.Knowledge knowledge = properties.getKnowledge();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(knowledge.getConnectTimeoutMillis());
        factory.setReadTimeout(knowledge.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(knowledge.getFileUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 查询一页文件变更。
     *
     * @param since 游标（不含）；{@code null} 表示全量
     * @param current 页码（1 起）
     * @param size 每页条数
     * @return 变更记录（按更新时间升序）
     */
    public List<RemoteFileChange> changes(LocalDateTime since, long current, long size) {
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
        Object data = requireData(response, "查询文件变更流");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return List.of();
        }
        Object records = dataMap.get(FIELD_RECORDS);
        if (!(records instanceof List<?> list)) {
            return List.of();
        }
        List<RemoteFileChange> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(toChange(map));
            }
        }
        return result;
    }

    /**
     * 读取文件元数据。
     *
     * @param fileId 文件ID
     * @return 元数据；文件不存在返回 {@code null}
     */
    public RemoteFileChange detail(Long fileId) {
        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(DETAIL_PATH, fileId)
                    .header(HEADER_TENANT, tenantHeader())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
        Object data = requireData(response, "读取文件元数据");
        return data instanceof Map<?, ?> map ? toChange(map) : null;
    }

    /**
     * 读取文件字节内容。
     *
     * @param fileId 文件ID
     * @return 文件内容；不存在返回 {@code null}
     */
    public byte[] content(Long fileId) {
        try {
            return restClient.get()
                    .uri(CONTENT_PATH, fileId)
                    .header(HEADER_TENANT, tenantHeader())
                    .retrieve()
                    .body(byte[].class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /**
     * 取当前租户请求头；缺失租户上下文时失败关闭，避免跨租户拉取文件。
     *
     * @return 租户ID字符串
     */
    private String tenantHeader() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new IllegalStateException("缺少租户上下文，无法调用文件服务");
        }
        return String.valueOf(tenantId);
    }

    /**
     * 校验统一响应体并取出 {@code data}。
     *
     * @param response 响应体
     * @param action 动作描述
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
     * 把响应的键值映射转换为变更记录（容错解析时间与长整型）。
     *
     * @param map 响应数据
     * @return 变更记录
     */
    private RemoteFileChange toChange(Map<?, ?> map) {
        RemoteFileChange change = new RemoteFileChange();
        change.setId(longValue(map.get("id")));
        change.setOriginalName(stringValue(map.get("originalName")));
        change.setBucket(stringValue(map.get("bucket")));
        change.setFileSize(longValue(map.get("fileSize")));
        change.setContentType(stringValue(map.get("contentType")));
        change.setMd5(stringValue(map.get("md5")));
        change.setStatus(intValue(map.get("status")));
        change.setUploaderId(longValue(map.get("uploaderId")));
        change.setDeleted(intValue(map.get("deleted")));
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
     * 解析整型（兼容数字与数字字符串）。
     *
     * @param value 原值
     * @return 整型；无法解析返回 {@code null}
     */
    private Integer intValue(Object value) {
        Long number = longValue(value);
        return number == null ? null : number.intValue();
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
                log.warn("无法解析文件更新时间: {}", text);
                return null;
            }
        }
    }
}
