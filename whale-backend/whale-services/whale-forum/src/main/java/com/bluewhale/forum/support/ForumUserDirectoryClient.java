package com.bluewhale.forum.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.forum.config.ForumProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * whale-service-auth 内网通讯录客户端，用于把 @昵称 解析成用户 ID。
 *
 * <p>复用平台既有 {@code GET /user/v1/internal/directory}（通讯录里手机号/邮箱已脱敏），
 * 与 {@link ForumNotifyClient} 同口径：<b>只解析、不抛异常</b>，解析不到的名字由调用方跳过。</p>
 *
 * <p><b>为什么按名字逐个查</b>：提及一次通常 1–3 人，逐个精确匹配比翻页拉全量通讯录便宜得多；
 * 模糊命中多条时按 nickname/username <b>精确等于</b>过滤，同名不去猜（宁可少通知一个人，
 * 也不能把不该看的人拉进来）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ForumUserDirectoryClient {

    /**
     * 内网通讯录路径。
     *
     * <p><b>不带版本段</b>：{@code UserInternalApi} 上声明了 {@code /user/v{version}/internal} 与
     * {@code version = "1"}，但 auth 服务未启用 Spring 的 API 版本策略（{@code spring.mvc.apiversion.*}），
     * 实际映射是 {@code /user/internal/directory}。实测 {@code /user/v1/internal/directory} 恒 401
     * （路径不存在 → 落到 {@code anyRequest().authenticated()}），改路径前必须先 curl 一次。</p>
     */
    private static final String DIRECTORY_PATH = "/user/internal/directory";

    /** 单次查询条数（模糊命中可能有多条，取前 10 条足够做精确筛选） */
    private static final long QUERY_SIZE = 10L;

    private final ForumProperties properties;

    /** 测试注入的客户端；为空时按当前内网令牌配置现场创建 */
    private final RestClient client;

    /**
     * 生产用构造。
     *
     * <p>此处自建带超时的 RestClient：{@link InternalTokenSupport#restClient()} 没有超时设置，
     * auth 不可达时会一直阻塞，而 @提及只是发帖的附属能力，不允许拖住主流程。</p>
     *
     * @param properties 论坛配置
     */
    @Autowired
    public ForumUserDirectoryClient(ForumProperties properties) {
        this(properties, buildTimeoutClient(properties));
    }

    /**
     * 构造带连接/读超时的内网客户端。
     *
     * @param properties 论坛配置
     * @return 客户端
     */
    private static RestClient buildTimeoutClient(ForumProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getDirectoryConnectTimeoutMillis());
        factory.setReadTimeout(properties.getDirectoryReadTimeoutMillis());
        return InternalTokenSupport.builder().requestFactory(factory).build();
    }

    /**
     * 测试用构造。
     *
     * @param properties 论坛配置
     * @param client 外部客户端（可绑定 MockRestServiceServer）
     */
    ForumUserDirectoryClient(ForumProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 批量解析昵称。
     *
     * <p><b>必须带租户头</b>：auth 的通讯录按租户隔离，缺 {@code X-Tenant-Id} 时直接返回
     * 「缺少租户上下文」——不是查不到，而是整个请求失败。</p>
     *
     * @param names 昵称集合（可空）
     * @param tenantId 租户ID
     * @return 昵称 → 用户ID；解析不到的名字不出现在结果里
     */
    public Map<String, Long> resolve(Collection<String> names, Long tenantId) {
        Map<String, Long> resolved = new LinkedHashMap<>();
        if (names == null || names.isEmpty()) {
            return resolved;
        }
        for (String name : names) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            try {
                Long userId = findOne(name.trim(), tenantId);
                if (userId != null) {
                    resolved.put(name, userId);
                }
            } catch (RuntimeException error) {
                // 通讯录不可达不能让发帖失败：记一条 warn，这次提及就当没有
                log.warn("提及解析失败，已跳过：name={}, reason={}", name, error.getMessage());
            }
        }
        return resolved;
    }

    /**
     * 按昵称或账号精确查一个用户。
     *
     * @param name 昵称/账号
     * @param tenantId 租户ID
     * @return 用户ID；查不到返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private Long findOne(String name, Long tenantId) {
        String uri = UriComponentsBuilder.fromUriString(properties.getAuthBaseUrl() + DIRECTORY_PATH)
                .queryParam("keyword", name)
                .queryParam("current", 1)
                .queryParam("size", QUERY_SIZE)
                .build().encode().toUriString();
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        Map<String, Object> response = target.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (tenantId != null) {
                        headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                    }
                })
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        if (response == null || !"0".equals(String.valueOf(response.get("code")))) {
            log.debug("通讯录返回非成功：name={}, code={}, message={}", name,
                    response == null ? null : response.get("code"),
                    response == null ? null : response.get("message"));
            return null;
        }
        if (!(response.get("data") instanceof Map<?, ?> page)) {
            return null;
        }
        if (!(page.get("records") instanceof List<?> records)) {
            return null;
        }
        for (Object item : records) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> user = (Map<String, Object>) row;
            if (!name.equalsIgnoreCase(String.valueOf(user.get("nickname")))
                    && !name.equalsIgnoreCase(String.valueOf(user.get("username")))) {
                continue;
            }
            Object id = user.get("id");
            if (id instanceof Number number) {
                return number.longValue();
            }
            // 平台约定长整型一律序列化为字符串（前端 JS 精度），auth 通讯录的 id 正是字符串；
            // 只认 Number 会让「记录查到了但没命中」——日志里 records=2 却解析为空。
            if (id instanceof String text && !text.isBlank()) {
                try {
                    return Long.valueOf(text.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        log.debug("通讯录未精确命中：name={}, records={}", name, records.size());
        return null;
    }
}
