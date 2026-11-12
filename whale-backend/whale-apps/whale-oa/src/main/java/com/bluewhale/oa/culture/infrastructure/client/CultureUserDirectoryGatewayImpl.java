package com.bluewhale.oa.culture.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.oa.culture.domain.gateway.CultureUserDirectoryGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业文化通讯录网关实现。
 *
 * <p>调用 Auth 内网通讯录 {@code GET /user/v1/internal/directory}，仅用于补全姓名快照；
 * 服务不可达或返回异常时降级为空映射。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CultureUserDirectoryGatewayImpl implements CultureUserDirectoryGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 用户目录服务名 */
    private static final String SERVICE_NAME = "whale-service-auth";

    /** 单页拉取条数（Auth 上限 200） */
    private static final int PAGE_SIZE = 200;

    private final DiscoveryClient discoveryClient;

    @Override
    public Map<Long, String> resolveUserNames(int limit) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (limit <= 0) {
            return result;
        }
        long current = 1;
        while (result.size() < limit) {
            int size = Math.min(PAGE_SIZE, limit - result.size());
            Map<?, ?> response = fetch(current, size);
            List<Map<?, ?>> page = parse(response);
            if (page.isEmpty()) {
                break;
            }
            for (Map<?, ?> item : page) {
                Object id = item.get("id");
                if (!(id instanceof Number idNumber)) {
                    continue;
                }
                Object nickname = item.get("nickname");
                Object username = item.get("username");
                String name = nickname != null && !String.valueOf(nickname).isBlank()
                        ? String.valueOf(nickname) : (username == null ? null : String.valueOf(username));
                result.putIfAbsent(idNumber.longValue(), name);
            }
            if (page.size() < size) {
                break;
            }
            current++;
        }
        return result;
    }

    /**
     * 请求一页通讯录。
     *
     * @param current 当前页
     * @param size 每页条数
     * @return 响应信封，失败返回 null
     */
    private Map<?, ?> fetch(long current, int size) {
        String baseUrl = baseUrl();
        if (baseUrl == null) {
            return null;
        }
        String uri = UriComponentsBuilder.fromUriString(baseUrl + "/user/v1/internal/directory")
                .queryParam("current", current)
                .queryParam("size", size)
                .build().encode().toUri().toString();
        try {
            return InternalTokenSupport.restClient().get().uri(uri).retrieve().body(Map.class);
        } catch (RestClientResponseException | ResourceAccessException ex) {
            log.warn("企业文化通讯录查询失败: current={}, size={}, error={}", current, size, ex.getMessage());
            return null;
        }
    }

    /**
     * 解析通讯录分页记录。
     *
     * @param response 响应信封
     * @return 记录列表，失败返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<?, ?>> parse(Map<?, ?> response) {
        if (response == null) {
            return List.of();
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            log.warn("企业文化通讯录返回失败: message={}", response.get("message"));
            return List.of();
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> pageData)) {
            return List.of();
        }
        Object records = pageData.get("records");
        if (!(records instanceof List<?> list)) {
            return List.of();
        }
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> record) {
                result.add(record);
            }
        }
        return result;
    }

    /**
     * 解析用户目录服务实例地址。
     *
     * @return 服务基地址，未注册返回 null
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            log.warn("企业文化通讯录服务不可用: {}", SERVICE_NAME);
            return null;
        }
        return instances.getFirst().getUri().toString();
    }
}
