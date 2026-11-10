package com.bluewhale.twin.support;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.TwinNotifyRecipientVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 接收人检索客户端（Wave 6.11 M2）：代理 auth 内网通讯录。
 *
 * <p>为什么不让前端直接调 {@code /user/page}：那个端点要求 {@code sys:user:list}，
 * 孪生的场景运营者通常没有用户管理权限，会出现「配不了接收人」的死结。
 * 这里改走 {@code GET /user/internal/directory}（网关对 {@code /internal/**} 统一 403，
 * 外部不可达），由孪生服务带内网令牌代理，前端只拿到「ID + 名字 + 部门」。</p>
 *
 * <p>检索失败不抛异常：返回空列表并记 WARN——选择器拉不到人时前端可以退回手工输入，
 * 不该整页报错。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TwinUserDirectoryClient {

    /** 内网通讯录路径 */
    private static final String DIRECTORY_PATH = "/user/internal/directory";

    /** 单次返回上限（选择器只做增量搜索，不需要大页） */
    private static final int MAX_SIZE = 50;

    /** 成功业务码 */
    private static final String CODE_SUCCESS = "0";

    private final TwinProperties properties;

    /** 测试注入的客户端；为空时按内网令牌现场创建 */
    private final RestClient client;

    /**
     * 生产用构造：客户端在调用时按 {@link InternalTokenSupport} 当前配置创建。
     *
     * @param properties 孪生配置
     */
    @Autowired
    public TwinUserDirectoryClient(TwinProperties properties) {
        this(properties, null);
    }

    /**
     * 测试用构造：外部传入 RestClient（可绑定 MockRestServiceServer）。
     *
     * @param properties 孪生配置
     * @param client     调用客户端（为空表示按内网令牌现场创建）
     */
    TwinUserDirectoryClient(TwinProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * 按关键字检索候选接收人。
     *
     * <p>必须带 {@code X-Tenant-Id}：auth 的内网通讯录强制要求租户上下文
     * （缺失直接 400「缺少租户上下文，请通过网关访问」），少了这一头检索必然空。</p>
     *
     * @param tenantId 租户ID（可空，空则不带头——此时 auth 会拒绝）
     * @param keyword  关键字（用户名/昵称/手机号；可空表示不过滤）
     * @param size     条数（1~50，空则 20）
     * @return 候选接收人；检索失败返回空列表
     */
    public List<TwinNotifyRecipientVO> search(Long tenantId, String keyword, Integer size) {
        int limit = size == null ? 20 : Math.min(MAX_SIZE, Math.max(1, size));
        String baseUrl = properties.getAlertNotify().getAuthBaseUrl();
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        // 用 UriComponentsBuilder 拼：直接 path(baseUrl + ...) 会把 "http://" 里的双斜杠吃掉
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUrl + DIRECTORY_PATH);
        if (StringUtils.hasText(keyword)) {
            uriBuilder.queryParam("keyword", keyword.trim());
        }
        uriBuilder.queryParam("current", 1).queryParam("size", limit);
        try {
            Map<String, Object> response = target.get()
                    .uri(uriBuilder.encode().build().toUri())
                    .headers(headers -> {
                        if (tenantId != null) {
                            headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                        }
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return parse(response);
        } catch (RuntimeException error) {
            log.warn("接收人检索失败（返回空列表）：keyword={}, reason={}", keyword, error.getMessage());
            return List.of();
        }
    }

    /**
     * 解析通讯录响应：业务码非 0 或结构不符时返回空列表。
     *
     * @param response 响应信封
     * @return 候选接收人
     */
    @SuppressWarnings("unchecked")
    private List<TwinNotifyRecipientVO> parse(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        Object code = response.get("code");
        if (code != null && !CODE_SUCCESS.equals(String.valueOf(code))) {
            log.warn("接收人检索业务失败：{}", response.get("message"));
            return List.of();
        }
        if (!(response.get("data") instanceof Map<?, ?> data)) {
            return List.of();
        }
        if (!(data.get("records") instanceof List<?> records)) {
            return List.of();
        }
        List<TwinNotifyRecipientVO> result = new ArrayList<>(records.size());
        for (Object item : records) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Long id = asLong(row.get("id"));
            if (id == null) {
                continue;
            }
            String username = asText(row.get("username"));
            String nickname = asText(row.get("nickname"));
            result.add(TwinNotifyRecipientVO.builder()
                    .id(String.valueOf(id))
                    .name(StringUtils.hasText(nickname) ? nickname : username)
                    .username(username)
                    .deptName(asText(row.get("deptName")))
                    .build());
        }
        return result;
    }

    /**
     * 取长整型字段（兼容数字与数字字符串）。
     *
     * @param value 原始值
     * @return 长整型或 {@code null}
     */
    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException error) {
                return null;
            }
        }
        return null;
    }

    /**
     * 取文本字段。
     *
     * @param value 原始值
     * @return 文本或 {@code null}
     */
    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
