package com.bluewhale.agent.support.index;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 中文领域词 → 代码标识符别名扩展。
 *
 * <p>平台知识大量以类名、模块名、配置键等英文标识符存在（如 {@code whale-gateway}、
 * {@code routes}），仅用中文分词无法命中。本类维护最小别名表，把问题中的中文领域词
 * 扩展为英文检索词，提升「网关路由」「认证服务」类问题的召回与排序。</p>
 *
 * @author BlueWhale
 */
public final class QueryAliasExpander {

    /** 中文领域词 → 英文检索词 */
    private static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("网关", List.of("gateway", "route", "routes"));
        ALIASES.put("路由", List.of("route", "routes", "predicate", "path"));
        ALIASES.put("认证", List.of("auth", "authentication", "token", "jwt"));
        ALIASES.put("登录", List.of("login", "auth", "token"));
        ALIASES.put("权限", List.of("permission", "role", "auth"));
        ALIASES.put("用户", List.of("user", "sys_user"));
        ALIASES.put("部门", List.of("dept", "sys_dept"));
        ALIASES.put("角色", List.of("role", "sys_role"));
        ALIASES.put("菜单", List.of("menu", "sys_menu"));
        ALIASES.put("工作流", List.of("workflow", "process", "instance", "task"));
        ALIASES.put("流程", List.of("workflow", "process", "bpmn"));
        ALIASES.put("表单", List.of("form", "schema"));
        ALIASES.put("文件", List.of("file", "upload"));
        ALIASES.put("定时", List.of("job", "schedule", "cron"));
        ALIASES.put("任务", List.of("job", "task"));
        ALIASES.put("审计", List.of("audit", "log"));
        ALIASES.put("告警", List.of("alert", "rule"));
        ALIASES.put("支付", List.of("pay", "order"));
        ALIASES.put("国际化", List.of("i18n", "message"));
        ALIASES.put("监控", List.of("monitor", "health", "metrics"));
        ALIASES.put("数据库", List.of("mysql", "datasource", "sql"));
        ALIASES.put("表结构", List.of("table", "column", "create"));
        ALIASES.put("索引", List.of("index", "lucene"));
        ALIASES.put("端口", List.of("port", "server"));
        ALIASES.put("版本", List.of("version", "dependency", "pom"));
        ALIASES.put("依赖", List.of("dependency", "pom", "artifact"));
        ALIASES.put("注册", List.of("nacos", "discovery", "registry"));
        ALIASES.put("配置", List.of("config", "properties", "yaml"));
        ALIASES.put("服务", List.of("service", "module"));
    }

    /** 工具类不允许实例化 */
    private QueryAliasExpander() {
    }

    /**
     * 按问题中的中文领域词扩展英文检索词。
     *
     * @param question 用户问题
     * @return 去重后的英文检索词列表
     */
    public static List<String> expand(String question) {
        List<String> tokens = new ArrayList<>();
        if (question == null || question.isEmpty()) {
            return tokens;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                tokens.addAll(entry.getValue());
            }
        }
        return tokens;
    }
}
