package com.bluewhale.oa.collab.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.oa.collab.domain.gateway.CollabUserDirectoryGateway;
import com.bluewhale.oa.collab.domain.model.UserCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 协同通讯录网关实现。
 *
 * <p>调用 Auth 内网通讯录 {@code GET /user/internal/directory}，按页拉取候选用户；
 * 服务不可达时返回空列表，由调用方按业务提示处理。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollabUserDirectoryGatewayImpl implements CollabUserDirectoryGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 用户目录服务名 */
    private static final String SERVICE_NAME = "whale-service-auth";

    /** 单页拉取条数（Auth 上限 200） */
    private static final int PAGE_SIZE = 200;

    private final DiscoveryClient discoveryClient;

    @Override
    public List<UserCandidate> listUsers(String keyword, Long deptId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<UserCandidate> result = new ArrayList<>();
        long current = 1;
        while (result.size() < limit) {
            int size = Math.min(PAGE_SIZE, limit - result.size());
            Map<?, ?> response = fetch(keyword, deptId, current, size);
            List<UserCandidate> page = parse(response);
            if (page.isEmpty()) {
                break;
            }
            result.addAll(page);
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
     * @param keyword 关键字
     * @param deptId 部门ID
     * @param current 当前页
     * @param size 每页条数
     * @return 响应信封，失败返回 null
     */
    private Map<?, ?> fetch(String keyword, Long deptId, long current, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl() + "/user/internal/directory")
                .queryParam("current", current)
                .queryParam("size", size);
        if (StringUtils.hasText(keyword)) {
            builder.queryParam("keyword", keyword.trim());
        }
        if (deptId != null) {
            builder.queryParam("deptId", deptId);
        }
        String uri = builder.build().encode().toUri().toString();
        try {
            return InternalTokenSupport.restClient().get().uri(uri).retrieve().body(Map.class);
        } catch (RestClientResponseException | ResourceAccessException ex) {
            log.warn("协同通讯录查询失败: keyword={}, deptId={}, error={}", keyword, deptId, ex.getMessage());
            return null;
        }
    }

    /**
     * 解析通讯录分页记录。
     *
     * @param response 响应信封
     * @return 候选用户
     */
    @SuppressWarnings("unchecked")
    private List<UserCandidate> parse(Map<?, ?> response) {
        if (response == null) {
            return List.of();
        }
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            log.warn("协同通讯录返回失败: message={}", response.get("message"));
            return List.of();
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> page)) {
            return List.of();
        }
        Object records = page.get("records");
        if (!(records instanceof List<?> list)) {
            return List.of();
        }
        List<UserCandidate> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object id = map.get("id");
            if (!(id instanceof Number idNumber)) {
                continue;
            }
            UserCandidate candidate = new UserCandidate();
            candidate.setUserId(idNumber.longValue());
            candidate.setUsername(map.get("username") == null ? null : String.valueOf(map.get("username")));
            candidate.setNickname(map.get("nickname") == null ? null : String.valueOf(map.get("nickname")));
            Object dept = map.get("deptId");
            if (dept instanceof Number deptNumber) {
                candidate.setDeptId(deptNumber.longValue());
            }
            result.add(candidate);
        }
        return result;
    }

    /**
     * 解析用户目录服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new IllegalStateException("用户目录服务不可用：" + SERVICE_NAME);
        }
        return instances.getFirst().getUri().toString();
    }
}
