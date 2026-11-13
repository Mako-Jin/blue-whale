package com.bluewhale.gateway.actuator;

import com.bluewhale.gateway.route.NacosRouteDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态路由治理端点（G3）。
 *
 * <p>{@code GET /actuator/routeGovernance} 返回当前 / 上一版路由摘要与被拒绝配置次数；
 * {@code POST /actuator/routeGovernance} 回滚到上一版路由快照并触发网关重载。</p>
 *
 * @author BlueWhale
 */
@Component
@Endpoint(id = "routeGovernance")
@RequiredArgsConstructor
public class RouteGovernanceEndpoint {

    /** 路由摘要字段：路由 id */
    private static final String FIELD_ID = "id";

    /** 路由摘要字段：目标地址 */
    private static final String FIELD_URI = "uri";

    /** 路由摘要字段：匹配优先级（未配置时为默认 0） */
    private static final String FIELD_ORDER = "order";

    /** 路由摘要字段：断言名称列表 */
    private static final String FIELD_PREDICATES = "predicates";

    /** 路由摘要字段：过滤器名称列表 */
    private static final String FIELD_FILTERS = "filters";

    private final NacosRouteDefinitionRepository repository;

    /**
     * 查询当前 / 上一版路由摘要与被拒绝配置次数。
     *
     * @return 治理快照
     */
    @ReadOperation
    public Map<String, Object> governance() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("current", summarize(repository.getCurrentRoutes()));
        snapshot.put("previous", summarize(repository.getPreviousRoutes()));
        snapshot.put("currentCount", repository.getCurrentRoutes().size());
        snapshot.put("previousCount", repository.getPreviousRoutes().size());
        snapshot.put("rejectedCount", repository.getRejectedCount());
        return snapshot;
    }

    /**
     * 回滚到上一版路由快照。
     *
     * @return 回滚结果（是否成功 + 回滚后路由数）
     */
    @WriteOperation
    public Map<String, Object> rollback() {
        boolean rolledBack = repository.rollback();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rolledBack", rolledBack);
        result.put("routeCount", repository.getCurrentRoutes().size());
        return result;
    }

    /**
     * 将路由定义列表压缩为摘要（仅保留 id / uri / 断言与过滤器名称）。
     *
     * @param definitions 路由定义列表
     * @return 路由摘要列表
     */
    private static List<Map<String, Object>> summarize(List<RouteDefinition> definitions) {
        return definitions.stream().map(definition -> {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put(FIELD_ID, definition.getId());
            summary.put(FIELD_URI, definition.getUri() == null ? null : definition.getUri().toString());
            summary.put(FIELD_ORDER, definition.getOrder());
            summary.put(FIELD_PREDICATES, definition.getPredicates().stream()
                    .map(predicate -> predicate.getName()).toList());
            summary.put(FIELD_FILTERS, definition.getFilters().stream()
                    .map(filter -> filter.getName()).toList());
            return summary;
        }).toList();
    }
}
