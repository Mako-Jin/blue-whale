package com.bluewhale.agent.support.qa;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.entity.AgentFact;
import com.bluewhale.agent.service.FactStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事实召回：从问题中提取服务名、表名、接口路径与英文标识符，按类型精确/模糊查询 {@code agent_fact}。
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FactRetriever {

    /** 服务名（如 whale-agent） */
    private static final Pattern SERVICE_NAME = Pattern.compile("\\bwhale-[a-z0-9][a-z0-9-]*\\b");

    /** 表名（如 agent_fact） */
    private static final Pattern TABLE_NAME = Pattern.compile("\\b[a-z][a-z0-9]*_[a-z0-9_]+\\b");

    /** 接口路径（如 /agent/chat、/agent/**） */
    private static final Pattern API_PATH = Pattern.compile("/[a-zA-Z0-9_{}*/-]{2,}");

    /** 英文标识符（依赖坐标片段） */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9.+\\-]{2,}");

    /** 已知依赖别名：问题词 → 依赖 artifactId */
    private static final Map<String, String> DEPENDENCY_ALIASES = Map.of(
            "spring boot", "spring-boot-starter-parent",
            "springboot", "spring-boot-starter-parent",
            "spring cloud alibaba", "spring-cloud-alibaba-dependencies",
            "spring cloud", "spring-cloud-dependencies");

    private final FactStoreService factStoreService;

    private final AgentProperties properties;

    /**
     * 召回与问题相关的事实（含运行时事实）。
     *
     * @param question 用户问题
     * @return 去重后的事实列表
     */
    public List<AgentFact> retrieve(String question) {
        return retrieve(question, true);
    }

    /**
     * 召回与问题相关的事实。
     *
     * @param question       用户问题
     * @param includeRuntime 是否召回运行时事实（无 {@code sys:agent:runtime:read} 权限时为 {@code false}）
     * @return 去重后的事实列表
     */
    public List<AgentFact> retrieve(String question, boolean includeRuntime) {
        Map<String, AgentFact> merged = new LinkedHashMap<>();
        collectServices(question, merged, includeRuntime);
        collectTables(question, merged);
        collectEndpoints(question, merged);
        collectDependencies(question, merged);
        int max = properties.getQa().getMaxFacts();
        List<AgentFact> facts = new ArrayList<>(merged.values());
        return facts.size() <= max ? facts : facts.subList(0, max);
    }

    /**
     * 服务相关事实（SERVICE/PORT/ROUTE/RUNTIME_SERVICE）。
     *
     * @param question       问题
     * @param merged         聚合容器
     * @param includeRuntime 是否包含运行时事实
     */
    private void collectServices(String question, Map<String, AgentFact> merged, boolean includeRuntime) {
        for (String service : matches(SERVICE_NAME, question)) {
            add(merged, factStoreService.query("SERVICE", service, 1));
            add(merged, factStoreService.query("PORT", service, 1));
            add(merged, factStoreService.query("ROUTE", service, 1));
            if (includeRuntime) {
                add(merged, factStoreService.query(FactStoreService.RUNTIME_SERVICE, service, 1));
            }
        }
    }

    /**
     * 表与字段相关事实（TABLE/COLUMN）。
     *
     * @param question 问题
     * @param merged   聚合容器
     */
    private void collectTables(String question, Map<String, AgentFact> merged) {
        for (String table : matches(TABLE_NAME, question)) {
            add(merged, factStoreService.query("TABLE", table, 1));
            // COLUMN 跨多个 DDL 文件存在重复行，先取足量再按 factKey 去重，避免排序靠后的字段被截断
            add(merged, factStoreService.queryLike("COLUMN", table + ".", 100));
        }
    }

    /**
     * 接口路径相关事实（ENDPOINT）。
     *
     * @param question 问题
     * @param merged   聚合容器
     */
    private void collectEndpoints(String question, Map<String, AgentFact> merged) {
        for (String path : matches(API_PATH, question)) {
            add(merged, factStoreService.queryLike("ENDPOINT", path, 5));
            add(merged, factStoreService.queryLike("ROUTE", path, 3));
            add(merged, factStoreService.queryValueLike("ROUTE", path, 3));
        }
    }

    /**
     * 依赖版本相关事实（DEPENDENCY）：已知依赖别名优先，未命中时回退到英文标识符模糊匹配。
     *
     * @param question 问题
     * @param merged   聚合容器
     */
    private void collectDependencies(String question, Map<String, AgentFact> merged) {
        if (question != null) {
            String lower = question.toLowerCase();
            boolean aliased = false;
            for (Map.Entry<String, String> entry : DEPENDENCY_ALIASES.entrySet()) {
                if (lower.contains(entry.getKey())) {
                    // 依赖 factKey 带 groupId 前缀（如 org.springframework.boot:spring-boot-starter-parent），需模糊匹配
                    add(merged, factStoreService.queryLike("DEPENDENCY", entry.getValue(), 3));
                    aliased = true;
                }
            }
            if (aliased) {
                return;
            }
        }
        int queries = 0;
        for (String token : matches(IDENTIFIER, question)) {
            if (token.length() < 4 || queries >= 3) {
                continue;
            }
            add(merged, factStoreService.queryLike("DEPENDENCY", token, 3));
            queries++;
        }
    }

    /**
     * 加入事实并按 {@code type#key} 去重。
     *
     * @param merged 聚合容器
     * @param facts  待加入事实
     */
    private static void add(Map<String, AgentFact> merged, List<AgentFact> facts) {
        for (AgentFact fact : facts) {
            merged.putIfAbsent(fact.getFactType() + "#" + fact.getFactKey(), fact);
        }
    }

    /**
     * 提取正则命中项（保留首次出现顺序）。
     *
     * @param pattern 正则
     * @param text    文本
     * @return 命中项
     */
    private static List<String> matches(Pattern pattern, String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group().trim();
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }
}
