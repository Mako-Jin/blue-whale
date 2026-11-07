package com.bluewhale.oa.directory.infrastructure.client;

import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.directory.domain.gateway.DirectoryGateway;
import com.bluewhale.oa.directory.domain.model.DeptNode;
import com.bluewhale.oa.directory.domain.model.DirectoryUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通讯录网关实现。
 *
 * <p>调用 Auth 内网通讯录 {@code GET /user/internal/directory} 与
 * {@code GET /dept/internal/tree}；服务不可达或响应异常时统一抛出
 * {@link OaErrorCode#DIRECTORY_UNAVAILABLE}，由接口层转为 {@code 503}。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DirectoryGatewayImpl implements DirectoryGateway {

    /** 成功业务码 */
    private static final int CODE_SUCCESS = 0;

    /** 用户目录服务名 */
    private static final String SERVICE_NAME = "whale-service-auth";

    private final DiscoveryClient discoveryClient;

    @Override
    public PageResult<DirectoryUser> pageUsers(String keyword, Long deptId, long current, long size) {
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
        Map<?, ?> response = request(builder.build().encode().toUri().toString());
        return parsePage(response, current, size);
    }

    @Override
    public List<DeptNode> deptTree() {
        Map<?, ?> response = request(baseUrl() + "/dept/internal/tree");
        Object code = response.get("code");
        if (!(code instanceof Number number) || number.intValue() != CODE_SUCCESS) {
            log.warn("通讯录部门树返回失败: message={}", response.get("message"));
            throw unavailable();
        }
        Object data = response.get("data");
        return data instanceof List<?> list ? parseNodes(list) : List.of();
    }

    /**
     * 请求内网通讯录接口。
     *
     * @param uri 请求地址
     * @return 响应信封
     */
    private Map<?, ?> request(String uri) {
        try {
            Map<?, ?> response = InternalTokenSupport.restClient()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw unavailable();
            }
            return response;
        } catch (RuntimeException ex) {
            log.warn("通讯录服务调用失败: uri={}, error={}", uri, ex.getMessage());
            throw unavailable();
        }
    }

    /**
     * 解析用户分页记录。
     *
     * @param response 响应信封
     * @param current 当前页
     * @param size 每页条数
     * @return 分页结果
     */
    private PageResult<DirectoryUser> parsePage(Map<?, ?> response, long current, long size) {
        Object code = response.get("code");
        if (!(code instanceof Number codeNumber) || codeNumber.intValue() != CODE_SUCCESS) {
            log.warn("通讯录查询返回失败: message={}", response.get("message"));
            throw unavailable();
        }
        Object data = response.get("data");
        if (!(data instanceof Map<?, ?> page)) {
            throw unavailable();
        }
        Object records = page.get("records");
        List<DirectoryUser> users = records instanceof List<?> list ? parseUsers(list) : List.of();
        Object total = page.get("total");
        long totalValue;
        if (total instanceof Number totalNumber) {
            totalValue = totalNumber.longValue();
        } else if (users.size() >= size) {
            totalValue = current * size;
        } else {
            totalValue = (current - 1) * size + users.size();
        }
        return PageResult.of(users, totalValue, current, size);
    }

    /**
     * 解析通讯录用户列表。
     *
     * @param records 原始记录
     * @return 用户列表
     */
    private List<DirectoryUser> parseUsers(List<?> records) {
        List<DirectoryUser> users = new ArrayList<>();
        for (Object item : records) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object id = map.get("id");
            if (!(id instanceof Number idNumber)) {
                continue;
            }
            DirectoryUser user = new DirectoryUser();
            user.setId(idNumber.longValue());
            user.setUsername(textOf(map.get("username")));
            user.setNickname(textOf(map.get("nickname")));
            user.setDeptId(numberOf(map.get("deptId")));
            user.setDeptName(textOf(map.get("deptName")));
            user.setPhone(textOf(map.get("phone")));
            user.setEmail(textOf(map.get("email")));
            users.add(user);
        }
        return users;
    }

    /**
     * 递归解析部门树节点。
     *
     * @param items 原始节点
     * @return 部门树
     */
    private List<DeptNode> parseNodes(List<?> items) {
        List<DeptNode> nodes = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object id = map.get("id");
            if (!(id instanceof Number idNumber)) {
                continue;
            }
            DeptNode node = new DeptNode();
            node.setId(idNumber.longValue());
            node.setParentId(numberOf(map.get("parentId")));
            node.setName(textOf(map.get("deptName")));
            Object children = map.get("children");
            node.setChildren(children instanceof List<?> list ? parseNodes(list) : List.of());
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 提取字符串字段。
     *
     * @param value 原始值
     * @return 字符串
     */
    private static String textOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 提取数字字段。
     *
     * @param value 原始值
     * @return 数值
     */
    private static Long numberOf(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * 解析用户目录服务实例地址。
     *
     * @return 服务基地址
     */
    private String baseUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_NAME);
        if (instances.isEmpty()) {
            throw new IllegalStateException("通讯录服务不可用：" + SERVICE_NAME);
        }
        return instances.getFirst().getUri().toString();
    }

    /**
     * 构造通讯录服务不可用异常。
     *
     * @return 业务异常
     */
    private static RuntimeException unavailable() {
        return OaErrorCode.exception(OaErrorCode.DIRECTORY_UNAVAILABLE, "通讯录服务不可用");
    }
}
