package com.bluewhale.gateway.route;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RouteDefinitionValidator} 单元测试（G3）：必填字段、uri scheme 白名单、断言/过滤器名称白名单
 * 与 Path 一级前缀约束。
 *
 * @author BlueWhale
 */
class RouteDefinitionValidatorTest {

    /**
     * 构造一条基础合法路由定义
     *
     * @param id       路由 id
     * @param uri      目标地址
     * @param patterns Path 断言模式
     * @return 路由定义
     */
    private static RouteDefinition route(String id, String uri, String patterns) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(id);
        definition.setUri(URI.create(uri));
        PredicateDefinition predicate = new PredicateDefinition();
        predicate.setName("Path");
        predicate.setArgs(patterns == null ? Map.of() : Map.of("patterns", patterns));
        definition.setPredicates(List.of(predicate));
        definition.setFilters(new ArrayList<>());
        return definition;
    }

    @Test
    @DisplayName("validate：合法路由（lb + Path 一级前缀 + 空过滤器）通过校验")
    void shouldAcceptValidRoute() {
        assertTrue(RouteDefinitionValidator.isValid(route("whale-demo", "lb://whale-demo", "/demo/**")));
        assertTrue(RouteDefinitionValidator.isValid(route("whale-http", "http://127.0.0.1:9000", "/a,/b/**")));
    }

    @Test
    @DisplayName("validate：null 定义非法")
    void shouldRejectNullDefinition() {
        assertFalse(RouteDefinitionValidator.isValid(null));
    }

    @Test
    @DisplayName("validate：缺失或空白 id 非法")
    void shouldRejectBlankId() {
        assertFalse(RouteDefinitionValidator.isValid(route(null, "lb://whale-demo", "/demo/**")));
        assertFalse(RouteDefinitionValidator.isValid(route("  ", "lb://whale-demo", "/demo/**")));
    }

    @Test
    @DisplayName("validate：缺失 uri 非法")
    void shouldRejectMissingUri() {
        RouteDefinition definition = route("whale-demo", "lb://whale-demo", "/demo/**");
        definition.setUri(null);

        assertFalse(RouteDefinitionValidator.isValid(definition));
    }

    @Test
    @DisplayName("validate：uri scheme 不在白名单（file/ws）非法")
    void shouldRejectUnsupportedScheme() {
        assertFalse(RouteDefinitionValidator.isValid(route("whale-file", "file:///tmp/x", "/demo/**")));
        assertFalse(RouteDefinitionValidator.isValid(route("whale-ws", "ws://127.0.0.1:9000", "/demo/**")));
    }

    @Test
    @DisplayName("validate：缺失或空 predicates 非法")
    void shouldRejectMissingPredicates() {
        RouteDefinition missing = route("whale-demo", "lb://whale-demo", "/demo/**");
        missing.setPredicates(null);
        RouteDefinition empty = route("whale-demo", "lb://whale-demo", "/demo/**");
        empty.setPredicates(List.of());

        assertFalse(RouteDefinitionValidator.isValid(missing));
        assertFalse(RouteDefinitionValidator.isValid(empty));
    }

    @Test
    @DisplayName("validate：predicate 不在白名单非法")
    void shouldRejectUnlistedPredicate() {
        RouteDefinition definition = route("whale-demo", "lb://whale-demo", "/demo/**");
        PredicateDefinition predicate = new PredicateDefinition();
        predicate.setName("DangerousPredicate");
        definition.setPredicates(List.of(predicate));

        assertFalse(RouteDefinitionValidator.isValid(definition));
    }

    @Test
    @DisplayName("validate：Path 断言缺少 patterns 或为全域匹配非法")
    void shouldRejectIllegalPathPatterns() {
        RouteDefinition missing = route("whale-demo", "lb://whale-demo", null);
        assertFalse(RouteDefinitionValidator.isValid(missing));

        assertFalse(RouteDefinitionValidator.isValid(route("whale-demo", "lb://whale-demo", "/**")));
        assertFalse(RouteDefinitionValidator.isValid(route("whale-demo", "lb://whale-demo", "demo/**")));
        assertFalse(RouteDefinitionValidator.isValid(route("whale-demo", "lb://whale-demo", "/demo/**,/**")));
    }

    @Test
    @DisplayName("validate：filters 为 null 非法，空列表合法")
    void shouldRequireFiltersField() {
        RouteDefinition missing = route("whale-demo", "lb://whale-demo", "/demo/**");
        missing.setFilters(null);

        assertFalse(RouteDefinitionValidator.isValid(missing));
        assertTrue(RouteDefinitionValidator.isValid(route("whale-demo", "lb://whale-demo", "/demo/**")));
    }

    @Test
    @DisplayName("validate：filter 不在白名单（含 StripPrefix）非法")
    void shouldRejectUnlistedFilter() {
        RouteDefinition definition = route("whale-demo", "lb://whale-demo", "/demo/**");
        FilterDefinition filter = new FilterDefinition();
        filter.setName("StripPrefix");
        definition.setFilters(List.of(filter));

        assertFalse(RouteDefinitionValidator.isValid(definition));
    }

    @Test
    @DisplayName("validate：多条 RouteDefinition 逐条校验")
    void shouldValidateEveryRouteInList() {
        List<String> errors = new ArrayList<>();
        errors.addAll(RouteDefinitionValidator.validate(route("ok", "lb://ok", "/ok/**")));
        assertTrue(errors.isEmpty());

        errors.addAll(RouteDefinitionValidator.validate(route("bad", "lb://bad", "/**")));
        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("validateAll：id 唯一时通过，重复时报错")
    void shouldRejectDuplicateRouteIds() {
        List<RouteDefinition> unique = List.of(
                route("whale-a", "lb://whale-a", "/a/**"),
                route("whale-b", "lb://whale-b", "/b/**"));
        assertTrue(RouteDefinitionValidator.validateAll(unique).isEmpty());

        List<RouteDefinition> duplicated = List.of(
                route("whale-a", "lb://whale-a", "/a/**"),
                route("whale-a", "lb://whale-b", "/b/**"));
        List<String> errors = RouteDefinitionValidator.validateAll(duplicated);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("路由 id 重复：whale-a"));
    }
}
