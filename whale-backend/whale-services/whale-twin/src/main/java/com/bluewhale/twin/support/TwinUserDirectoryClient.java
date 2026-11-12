package com.bluewhale.twin.support;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.TwinNotifyDeptVO;
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
 * 接收人检索客户端（Wave 6.11 M2，Wave 6.12 补分页与部门树）：代理 auth 内网通讯录。
 *
 * <p>为什么不让前端直接调 {@code /user/page}：那个端点要求 {@code sys:user:list}，
 * 孪生的场景运营者通常没有用户管理权限，会出现「配不了接收人」的死结。
 * 这里改走 {@code GET /user/v1/internal/directory} 与 {@code GET /dept/v1/internal/tree}
 * （网关对 {@code /internal/**} 统一 403，外部不可达），由孪生服务带内网令牌代理，
 * 前端只拿到「ID + 名字 + 部门」。</p>
 *
 * <p>检索失败不抛异常：返回空页/空树并记 WARN——选择器拉不到人时前端可以退回手工输入，
 * 不该整页报错。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class TwinUserDirectoryClient {

    /** 内网通讯录路径 */
    private static final String DIRECTORY_PATH = "/user/v1/internal/directory";

    /** 内网部门树路径 */
    private static final String DEPT_TREE_PATH = "/dept/v1/internal/tree";

    /** 单次返回上限（选择器只做增量搜索，不需要大页） */
    private static final int MAX_SIZE = 50;

    /** 默认每页条数 */
    private static final int DEFAULT_SIZE = 20;

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
     * 按关键字/部门分页检索候选接收人。
     *
     * <p>必须带 {@code X-Tenant-Id}：auth 的内网通讯录强制要求租户上下文
     * （缺失直接 400「缺少租户上下文，请通过网关访问」），少了这一头检索必然空。</p>
     *
     * @param tenantId 租户ID（可空，空则不带头——此时 auth 会拒绝）
     * @param keyword  关键字（用户名/昵称/手机号；可空表示不过滤）
     * @param deptId   部门ID（可空表示不限部门）
     * @param current  页码（空或小于 1 按 1）
     * @param size     每页条数（空按 20，上限 50）
     * @return 分页结果（失败时为空页，{@code total=0}）
     */
    public PageResult<TwinNotifyRecipientVO> search(Long tenantId, String keyword, Long deptId,
                                                    Long current, Integer size) {
        long safeCurrent = current == null || current < 1 ? 1L : current;
        long safeSize = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(properties.getAlertNotify().getAuthBaseUrl() + DIRECTORY_PATH);
        if (StringUtils.hasText(keyword)) {
            uriBuilder.queryParam("keyword", keyword.trim());
        }
        if (deptId != null) {
            // 选父部门时把子部门的人也算进来，否则「选生产部却一个人都没有」很反直觉
            uriBuilder.queryParam("deptId", deptId).queryParam("includeChildren", true);
        }
        uriBuilder.queryParam("current", safeCurrent).queryParam("size", safeSize);
        Map<String, Object> response = get(tenantId, uriBuilder, "接收人检索");
        return parsePage(response, safeCurrent, safeSize);
    }

    /**
     * 查询当前租户全量部门树（供接收人选择器按部门筛人）。
     *
     * @param tenantId 租户ID（可空，空则不带头——此时 auth 会拒绝）
     * @return 部门树；不可用或无部门时返回空列表
     */
    public List<TwinNotifyDeptVO> departments(Long tenantId) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(properties.getAlertNotify().getAuthBaseUrl() + DEPT_TREE_PATH);
        Map<String, Object> response = get(tenantId, uriBuilder, "部门树查询");
        return parseDepartments(response);
    }

    /**
     * 发起内网 GET 并解出响应信封。
     *
     * <p>传输异常一律折算成 {@code null}：调用方按「查不到」处理，不向上抛。</p>
     *
     * @param tenantId  租户ID（可空）
     * @param uriBuilder 已拼好参数的 URI 构造器
     * @param action    动作名（仅用于日志）
     * @return 响应信封；失败返回 {@code null}
     */
    private Map<String, Object> get(Long tenantId, UriComponentsBuilder uriBuilder, String action) {
        RestClient target = client == null ? InternalTokenSupport.restClient() : client;
        try {
            return target.get()
                    // 用 UriComponentsBuilder 拼：直接 path(baseUrl + ...) 会把 "http://" 里的双斜杠吃掉
                    .uri(uriBuilder.encode().build().toUri())
                    .headers(headers -> {
                        if (tenantId != null) {
                            headers.set(WebHeaders.TENANT_ID, String.valueOf(tenantId));
                        }
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RuntimeException error) {
            log.warn("{}失败（按空结果处理）：reason={}", action, error.getMessage());
            return null;
        }
    }

    /**
     * 解析通讯录分页响应：业务码非 0 或结构不符时返回空页。
     *
     * @param response 响应信封
     * @param current  已归一化的页码
     * @param size     已归一化的每页条数
     * @return 分页结果
     */
    private PageResult<TwinNotifyRecipientVO> parsePage(Map<String, Object> response,
                                                        long current, long size) {
        Map<?, ?> data = dataOf(response);
        if (data == null || !(data.get("records") instanceof List<?> records)) {
            return PageResult.of(List.of(), 0L, current, size);
        }
        List<TwinNotifyRecipientVO> result = new ArrayList<>(records.size());
        for (Object item : records) {
            TwinNotifyRecipientVO recipient = toRecipient(item);
            if (recipient != null) {
                result.add(recipient);
            }
        }
        Long total = asLong(data.get("total"));
        // total 缺失时至少等于本页条数，避免前端显示「共 0 人」却列出若干条
        return PageResult.of(result, total == null ? result.size() : total, current, size);
    }

    /**
     * 解析部门树响应：业务码非 0 或结构不符时返回空列表。
     *
     * @param response 响应信封
     * @return 部门树
     */
    private List<TwinNotifyDeptVO> parseDepartments(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        Object code = response.get("code");
        if (code != null && !CODE_SUCCESS.equals(String.valueOf(code))) {
            log.warn("部门树查询业务失败：{}", response.get("message"));
            return List.of();
        }
        if (!(response.get("data") instanceof List<?> rows)) {
            return List.of();
        }
        return toDeptNodes(rows);
    }

    /**
     * 递归转换部门节点：缺 ID 的节点连同子树一起丢弃（选了也筛不出人）。
     *
     * @param rows 原始节点列表
     * @return 部门节点列表
     */
    private List<TwinNotifyDeptVO> toDeptNodes(List<?> rows) {
        List<TwinNotifyDeptVO> nodes = new ArrayList<>(rows.size());
        for (Object item : rows) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Long id = asLong(row.get("id"));
            if (id == null) {
                continue;
            }
            List<TwinNotifyDeptVO> children = row.get("children") instanceof List<?> nested
                    ? toDeptNodes(nested) : null;
            nodes.add(TwinNotifyDeptVO.builder()
                    .id(String.valueOf(id))
                    .name(asText(row.get("deptName")))
                    .children(children != null && children.isEmpty() ? null : children)
                    .build());
        }
        return nodes;
    }

    /**
     * 取响应信封里的 {@code data} 映射。
     *
     * @param response 响应信封
     * @return 数据映射；业务码非 0 或结构不符时返回 {@code null}
     */
    private Map<?, ?> dataOf(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object code = response.get("code");
        if (code != null && !CODE_SUCCESS.equals(String.valueOf(code))) {
            log.warn("接收人检索业务失败：{}", response.get("message"));
            return null;
        }
        return response.get("data") instanceof Map<?, ?> data ? data : null;
    }

    /**
     * 单行通讯录 → 接收人候选项。
     *
     * @param item 原始行
     * @return 候选项；缺 ID 的行返回 {@code null}
     */
    private TwinNotifyRecipientVO toRecipient(Object item) {
        if (!(item instanceof Map<?, ?> row)) {
            return null;
        }
        Long id = asLong(row.get("id"));
        if (id == null) {
            return null;
        }
        String username = asText(row.get("username"));
        String nickname = asText(row.get("nickname"));
        return TwinNotifyRecipientVO.builder()
                .id(String.valueOf(id))
                .name(StringUtils.hasText(nickname) ? nickname : username)
                .username(username)
                .deptName(asText(row.get("deptName")))
                .build();
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
