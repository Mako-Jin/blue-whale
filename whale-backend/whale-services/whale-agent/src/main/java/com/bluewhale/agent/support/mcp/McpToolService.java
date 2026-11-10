package com.bluewhale.agent.support.mcp;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.entity.AgentFact;
import com.bluewhale.agent.service.AgentSearchService;
import com.bluewhale.agent.service.FactStoreService;
import com.bluewhale.agent.support.index.SearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 只读工具实现（轨 F）：复用检索与事实内核，不新增知识源。
 *
 * <p>工具集：{@code search_knowledge}、{@code get_fact}、{@code read_file}、{@code list_services}。
 * {@code read_file} 仅允许读取仓库内文本文件，并拦截构建产物、依赖目录与密钥文件。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class McpToolService {

    /** 运行时事实类型前缀（MCP 不暴露） */
    private static final String RUNTIME_TYPE_PREFIX = "RUNTIME";

    /** 读取文件上限（字节） */
    private static final long MAX_READ_BYTES = 1024L * 1024L;

    /** 二进制探测窗口（字节） */
    private static final int BINARY_PROBE_BYTES = 8192;

    /** 禁止读取的目录段 */
    private static final Set<String> DENIED_SEGMENTS = Set.of(
            ".git", "node_modules", "target", "dist", "work", "logs");

    /** 禁止读取的目录前缀 */
    private static final String DENIED_INDEX_PREFIX = "data/agent-index/";

    /** 禁止读取的密钥文件后缀 */
    private static final List<String> DENIED_SUFFIXES = List.of(".pem", ".key", ".p12", ".jks", ".keystore");

    private final AgentSearchService searchService;

    private final FactStoreService factStoreService;

    private final AgentProperties properties;

    /**
     * 检索知识库。
     *
     * @param arguments 工具入参（{@code query} 必填，{@code topK}/{@code kinds} 可选）
     * @return 命中列表
     */
    public Map<String, Object> searchKnowledge(Map<String, Object> arguments) {
        String query = requireText(arguments, "query");
        int max = properties.getMcp().getMaxSearchResults();
        int topK = Math.max(1, Math.min(intArg(arguments, "topK", properties.getSearch().getDefaultTopK()), max));
        Set<String> kinds = normalizeKinds(arguments.get("kinds"));
        List<SearchHit> hits = searchService.search(query, topK, kinds);
        List<Map<String, Object>> items = new ArrayList<>(hits.size());
        for (SearchHit hit : hits) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", hit.getPath());
            item.put("startLine", hit.getStartLine());
            item.put("endLine", hit.getEndLine());
            item.put("kind", hit.getKind());
            item.put("module", hit.getModule());
            item.put("title", hit.getTitle());
            item.put("fileId", hit.getFileId());
            item.put("url", hit.getUrl());
            item.put("score", hit.getScore());
            item.put("snippet", hit.getContent());
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("count", items.size());
        result.put("items", items);
        return result;
    }

    /**
     * 精确查询结构化事实。
     *
     * @param arguments 工具入参（{@code type}/{@code key}/{@code limit} 均可选）
     * @return 事实列表
     */
    public Map<String, Object> getFact(Map<String, Object> arguments) {
        String type = stringArg(arguments, "type");
        if (StringUtils.hasText(type) && type.trim().toUpperCase().startsWith(RUNTIME_TYPE_PREFIX)) {
            throw new IllegalArgumentException("MCP 只读工具不暴露运行时事实，请使用平台问答接口");
        }
        String key = stringArg(arguments, "key");
        int limit = Math.max(1, Math.min(intArg(arguments, "limit", 50), 200));
        List<AgentFact> facts = factStoreService.query(type, key, limit, false);
        List<Map<String, Object>> items = new ArrayList<>(facts.size());
        for (AgentFact fact : facts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", fact.getFactType());
            item.put("key", fact.getFactKey());
            item.put("value", factStoreService.parseValue(fact.getValueJson()));
            item.put("sourcePath", fact.getSourcePath());
            item.put("capturedAt", FactStoreService.formatTime(fact.getCapturedAt()));
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", items.size());
        result.put("items", items);
        return result;
    }

    /**
     * 读取仓库内文本文件片段。
     *
     * @param arguments 工具入参（{@code path} 必填，{@code startLine}/{@code endLine} 可选）
     * @return 文件片段
     */
    public Map<String, Object> readFile(Map<String, Object> arguments) {
        String relative = requireText(arguments, "path").replace('\\', '/');
        Path root = properties.resolveRepoRoot();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("路径必须位于仓库内");
        }
        try {
            Path realRoot = root.toRealPath();
            Path real = target.toRealPath();
            if (!real.startsWith(realRoot)) {
                throw new IllegalArgumentException("路径必须位于仓库内（符号链接越界）");
            }
            String canonical = realRoot.relativize(real).toString().replace('\\', '/');
            assertReadable(canonical);
            long size = Files.size(real);
            if (size > MAX_READ_BYTES) {
                throw new IllegalArgumentException("文件超过 1MB，请改用检索工具定位");
            }
            byte[] bytes = Files.readAllBytes(real);
            if (isBinary(bytes)) {
                throw new IllegalArgumentException("仅支持读取文本文件");
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = text.split("\n", -1);
            return slice(relative, lines, arguments);
        } catch (IOException e) {
            throw new IllegalArgumentException("文件不存在或不可读: " + relative);
        }
    }

    /**
     * 列出平台服务与端口事实。
     *
     * @return 服务与端口列表
     */
    public Map<String, Object> listServices() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("services", factItems(factStoreService.query("SERVICE", null, 200, false)));
        result.put("ports", factItems(factStoreService.query("PORT", null, 200, false)));
        return result;
    }

    /**
     * 校验路径可读性。
     *
     * @param canonical 仓库相对路径（正斜杠）
     */
    private void assertReadable(String canonical) {
        String lower = canonical.toLowerCase();
        for (String segment : lower.split("/")) {
            if (DENIED_SEGMENTS.contains(segment)) {
                throw new IllegalArgumentException("禁止读取构建产物或依赖目录: " + canonical);
            }
        }
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        if (lower.startsWith(DENIED_INDEX_PREFIX) || fileName.startsWith(".env")) {
            throw new IllegalArgumentException("禁止读取索引数据或环境变量文件: " + canonical);
        }
        for (String suffix : DENIED_SUFFIXES) {
            if (fileName.endsWith(suffix)) {
                throw new IllegalArgumentException("禁止读取密钥文件: " + canonical);
            }
        }
    }

    /**
     * 截取行区间并限制字符数。
     *
     * @param path    原始入参路径
     * @param lines   文件全部行
     * @param args    工具入参
     * @return 片段结果
     */
    private Map<String, Object> slice(String path, String[] lines, Map<String, Object> args) {
        int total = lines.length;
        // 文件以换行结尾时 split 会产生一个空尾行，不计入总行数
        if (total > 0 && lines[total - 1].isEmpty()) {
            total--;
        }
        int start = Math.max(1, Math.min(intArg(args, "startLine", 1), total));
        int requestedEnd = intArg(args, "endLine", 0);
        int maxLines = properties.getMcp().getMaxReadLines();
        int end = requestedEnd <= 0 ? total : Math.min(requestedEnd, total);
        end = Math.min(end, start + maxLines - 1);
        StringBuilder content = new StringBuilder();
        boolean charLimitHit = false;
        for (int i = start - 1; i < end; i++) {
            if (content.length() > 0) {
                content.append('\n');
            }
            content.append(lines[i]);
            if (content.length() >= properties.getMcp().getMaxReadChars()) {
                content.setLength(properties.getMcp().getMaxReadChars());
                charLimitHit = true;
                break;
            }
        }
        boolean truncated = charLimitHit || (requestedEnd <= 0 ? end < total : requestedEnd > end);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        result.put("startLine", start);
        result.put("endLine", end);
        result.put("totalLines", total);
        result.put("truncated", truncated);
        result.put("content", content.toString());
        return result;
    }

    /**
     * 二进制探测：前 8KB 出现 NUL 即视为二进制。
     *
     * @param bytes 文件字节
     * @return 是否二进制
     */
    private boolean isBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, BINARY_PROBE_BYTES);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 事实转精简视图。
     *
     * @param facts 事实列表
     * @return 精简条目
     */
    private List<Map<String, Object>> factItems(List<AgentFact> facts) {
        List<Map<String, Object>> items = new ArrayList<>(facts.size());
        for (AgentFact fact : facts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", fact.getFactKey());
            item.put("value", factStoreService.parseValue(fact.getValueJson()));
            item.put("sourcePath", fact.getSourcePath());
            items.add(item);
        }
        return items;
    }

    /**
     * 归一化知识类型过滤。
     *
     * @param raw 集合或逗号分隔串
     * @return 类型集合
     */
    private Set<String> normalizeKinds(Object raw) {
        Set<String> kinds = new LinkedHashSet<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    kinds.add(String.valueOf(item).trim());
                }
            }
            return kinds;
        }
        if (raw != null) {
            kinds.addAll(AgentSearchService.parseKinds(String.valueOf(raw)));
        }
        return kinds;
    }

    /**
     * 必填文本参数。
     *
     * @param args 入参
     * @param key  参数名
     * @return 非空文本
     */
    private String requireText(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("缺少必填参数: " + key);
        }
        return value.trim();
    }

    /**
     * 可选文本参数。
     *
     * @param args 入参
     * @param key  参数名
     * @return 文本或 null
     */
    private String stringArg(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 可选整数参数。
     *
     * @param args         入参
     * @param key          参数名
     * @param defaultValue 缺省值
     * @return 整数
     */
    private int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args == null ? null : args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
