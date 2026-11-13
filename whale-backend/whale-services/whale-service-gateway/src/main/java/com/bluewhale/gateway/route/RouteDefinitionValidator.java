package com.bluewhale.gateway.route;

import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 动态路由定义校验器（G3）。
 *
 * <p>对 Nacos 下发的 {@code whale.gateway.dynamic-routes} 逐条校验：
 * <ul>
 *   <li>必填：{@code id}、{@code uri}、{@code predicates}（非空）、{@code filters}（可为空列表）；</li>
 *   <li>{@code uri} scheme 白名单：{@code lb} / {@code http} / {@code https}；</li>
 *   <li>断言 / 过滤器 {@code name} 白名单，防止下发任意网关组件；</li>
 *   <li>{@code Path} 断言必须带一级路径前缀（如 {@code /workflow/**}），禁止 {@code /**} 全域匹配；</li>
 *   <li>同一批路由的 {@code id} 必须唯一（重复 id 会导致运行时按 id 建映射冲突）。</li>
 * </ul>
 * 任一条目非法即整体拒绝本次配置，网关保留上一版可用路由。</p>
 *
 * @author BlueWhale
 */
public final class RouteDefinitionValidator {

    /** uri 允许的 scheme 白名单 */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("lb", "http", "https");

    /** 允许的断言名称白名单 */
    private static final Set<String> ALLOWED_PREDICATES = Set.of(
            "Path", "Method", "Header", "Query", "Cookie", "Host", "RemoteAddr", "Weight",
            "After", "Before", "Between", "XForwardedRemoteAddr", "CloudFoundryRouteService");

    /** 允许的过滤器名称白名单（不含 StripPrefix：项目约定网关不剥离服务前缀） */
    private static final Set<String> ALLOWED_FILTERS = Set.of(
            "AddRequestHeader", "AddRequestParameter", "AddResponseHeader",
            "RemoveRequestHeader", "RemoveResponseHeader", "SetRequestHeader", "SetResponseHeader",
            "PrefixPath", "RewritePath", "RedirectTo", "Retry", "DedupeResponseHeader",
            "CircuitBreaker", "TokenRelay", "RequestRateLimiter", "SetPath", "SetStatus",
            "ModifyRequestBody", "ModifyResponseBody");

    /** Path 断言参数键 */
    private static final String PATH_ARG_KEY = "patterns";

    /** 允许多路径模式分隔符 */
    private static final String PATTERN_SEPARATOR = ",";

    /** 一级前缀路径模式：{@code /segment} 开头，允许后续通配 */
    private static final Pattern FIRST_LEVEL_PREFIX = Pattern.compile("^/[A-Za-z0-9._~-]+(/.*)?$");

    /** 工具类不允许实例化 */
    private RouteDefinitionValidator() {
    }

    /**
     * 校验单条路由定义。
     *
     * @param definition 路由定义，可为 {@code null}
     * @return 错误信息列表；合法时为空列表
     */
    public static List<String> validate(RouteDefinition definition) {
        List<String> errors = new ArrayList<>();
        if (definition == null) {
            errors.add("路由定义为空");
            return errors;
        }
        String id = definition.getId();
        if (id == null || id.isBlank()) {
            errors.add("路由缺少 id");
        }
        URI uri = definition.getUri();
        if (uri == null) {
            errors.add("路由缺少 uri");
        } else if (uri.getScheme() == null || !ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase())) {
            errors.add("路由 uri scheme 非法（允许 lb/http/https）：" + uri);
        }
        validatePredicates(id, definition.getPredicates(), errors);
        validateFilters(id, definition.getFilters(), errors);
        return errors;
    }

    /**
     * 校验一批路由定义：逐条校验 + id 唯一性校验。
     *
     * @param definitions 路由定义列表，可为 {@code null}
     * @return 错误信息列表；全部合法时为空列表
     */
    public static List<String> validateAll(List<RouteDefinition> definitions) {
        List<String> errors = new ArrayList<>();
        if (definitions == null) {
            return errors;
        }
        Set<String> ids = new HashSet<>();
        for (RouteDefinition definition : definitions) {
            errors.addAll(validate(definition));
            String id = definition == null ? null : definition.getId();
            if (id != null && !id.isBlank() && !ids.add(id)) {
                errors.add("路由 id 重复：" + id);
            }
        }
        return errors;
    }

    /**
     * 判断单条路由定义是否合法。
     *
     * @param definition 路由定义
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(RouteDefinition definition) {
        return validate(definition).isEmpty();
    }

    /**
     * 校验断言列表：非空、名称白名单、Path 断言必须带一级前缀。
     *
     * @param id 路由 id（用于错误信息）
     * @param predicates 断言列表
     * @param errors 错误信息收集器
     */
    private static void validatePredicates(String id, List<PredicateDefinition> predicates, List<String> errors) {
        if (predicates == null || predicates.isEmpty()) {
            errors.add("路由 " + id + " 缺少 predicates");
            return;
        }
        for (PredicateDefinition predicate : predicates) {
            String name = predicate == null ? null : predicate.getName();
            if (name == null || name.isBlank()) {
                errors.add("路由 " + id + " 存在缺少 name 的 predicate");
                continue;
            }
            if (!ALLOWED_PREDICATES.contains(name)) {
                errors.add("路由 " + id + " 的 predicate 不在白名单：" + name);
                continue;
            }
            if ("Path".equals(name)) {
                validatePathArgs(id, predicate.getArgs(), errors);
            }
        }
    }

    /**
     * 校验 Path 断言参数必须带一级路径前缀。
     *
     * @param id 路由 id（用于错误信息）
     * @param args 断言参数
     * @param errors 错误信息收集器
     */
    private static void validatePathArgs(String id, Map<String, String> args, List<String> errors) {
        String patterns = args == null ? null : args.get(PATH_ARG_KEY);
        if (patterns == null || patterns.isBlank()) {
            errors.add("路由 " + id + " 的 Path 断言缺少 patterns");
            return;
        }
        // Gateway 的 PathRoutePredicateFactory 不会按逗号切分 args.patterns：
        // `/a/**,/b/**` 会被当成一整个 pattern 编译，路由永不命中（表现为 404「服务不可用」，
        // 且不报任何错）。这里显式拦下来，逼着拆成多条路由。
        if (patterns.split(PATTERN_SEPARATOR).length > 1) {
            errors.add("路由 " + id + " 的 Path 断言只能写一个 pattern（Gateway 不按逗号切分，多写会导致该路由永不命中）："
                    + patterns);
            return;
        }
        for (String pattern : patterns.split(PATTERN_SEPARATOR)) {
            String trimmed = pattern.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!FIRST_LEVEL_PREFIX.matcher(trimmed).matches()) {
                errors.add("路由 " + id + " 的 Path 断言必须带一级前缀：" + trimmed);
            }
        }
    }

    /**
     * 校验过滤器列表：字段必须存在（可为空列表）、名称白名单。
     *
     * @param id 路由 id（用于错误信息）
     * @param filters 过滤器列表
     * @param errors 错误信息收集器
     */
    private static void validateFilters(String id, List<FilterDefinition> filters, List<String> errors) {
        if (filters == null) {
            errors.add("路由 " + id + " 缺少 filters");
            return;
        }
        for (FilterDefinition filter : filters) {
            String name = filter == null ? null : filter.getName();
            if (name == null || name.isBlank()) {
                errors.add("路由 " + id + " 存在缺少 name 的 filter");
                continue;
            }
            if (!ALLOWED_FILTERS.contains(name)) {
                errors.add("路由 " + id + " 的 filter 不在白名单：" + name);
            }
        }
    }
}
