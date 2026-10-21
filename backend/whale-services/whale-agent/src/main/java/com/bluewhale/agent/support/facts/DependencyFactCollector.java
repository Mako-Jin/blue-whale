package com.bluewhale.agent.support.facts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 依赖版本事实采集器：解析 Maven {@code pom.xml} 与前端 {@code package.json}。
 *
 * <p>产出 {@code DEPENDENCY} 事实，键为 {@code groupId:artifactId}（Maven）或包名（npm），
 * 值为版本与引用模块列表，供「Spring Boot / React 用的哪个版本」类问题精确回答。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class DependencyFactCollector implements FactCollector {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Maven 依赖块 */
    private static final Pattern DEPENDENCY = Pattern.compile(
            "<dependency>(.*?)</dependency>", Pattern.DOTALL);

    /** Maven parent 块 */
    private static final Pattern PARENT = Pattern.compile("<parent>(.*?)</parent>", Pattern.DOTALL);

    /** 项目自身版本（紧跟在顶层 artifactId 之后的 version） */
    private static final Pattern PROJECT_VERSION = Pattern.compile(
            "</artifactId>\\s*<version>([^<]+)</version>");

    /** Maven properties 块 */
    private static final Pattern PROPERTIES = Pattern.compile(
            "<properties>(.*?)</properties>", Pattern.DOTALL);

    /** 任意 XML 标签 */
    private static final Pattern TAG = Pattern.compile("<([A-Za-z0-9_.\\-]+)>([^<]*)</\\1>");

    /** 属性占位符 */
    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public List<FactRecord> collect(Path repoRoot) {
        Map<String, Dependency> merged = new TreeMap<>();
        collectPoms(repoRoot, merged);
        collectPackageJson(repoRoot, merged);
        List<FactRecord> facts = new ArrayList<>(merged.size());
        for (Map.Entry<String, Dependency> entry : merged.entrySet()) {
            Dependency dependency = entry.getValue();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("version", dependency.version);
            value.put("type", dependency.type);
            value.put("modules", new ArrayList<>(dependency.modules));
            value.put("sourcePath", dependency.sourcePath);
            facts.add(new FactRecord("DEPENDENCY", entry.getKey(), value, dependency.sourcePath));
        }
        return facts;
    }

    /**
     * 解析 Maven POM。
     *
     * @param repoRoot 仓库根
     * @param merged   聚合结果
     */
    private void collectPoms(Path repoRoot, Map<String, Dependency> merged) {
        List<Path> poms = FactFileSupport.walk(repoRoot.resolve("backend"),
                path -> "pom.xml".equals(path.getFileName().toString()));
        for (Path pom : poms) {
            String text = FactFileSupport.read(pom);
            if (text.isEmpty()) {
                continue;
            }
            Map<String, String> properties = properties(text);
            String module = moduleOf(FactFileSupport.relative(repoRoot, pom));
            String sourcePath = FactFileSupport.relative(repoRoot, pom);
            String parentBlock = parentBlock(text);
            if (parentBlock != null) {
                String groupId = tag(parentBlock, "groupId");
                String artifactId = tag(parentBlock, "artifactId");
                if (groupId != null && artifactId != null) {
                    String parentVersion = resolve(tag(parentBlock, "version"), properties);
                    merge(merged, groupId + ":" + artifactId, parentVersion, "maven-parent", module, sourcePath);
                }
            }
            Matcher matcher = DEPENDENCY.matcher(text);
            while (matcher.find()) {
                String block = matcher.group(1);
                String groupId = tag(block, "groupId");
                String artifactId = tag(block, "artifactId");
                if (groupId == null || artifactId == null) {
                    continue;
                }
                String version = resolve(tag(block, "version"), properties);
                merge(merged, groupId + ":" + artifactId, version, "maven", module, sourcePath);
            }
        }
    }

    /**
     * 解析前端 package.json。
     *
     * @param repoRoot 仓库根
     * @param merged   聚合结果
     */
    private void collectPackageJson(Path repoRoot, Map<String, Dependency> merged) {
        List<Path> packages = FactFileSupport.walk(repoRoot.resolve("frontend"),
                path -> "package.json".equals(path.getFileName().toString()));
        for (Path file : packages) {
            try {
                JsonNode root = MAPPER.readTree(FactFileSupport.read(file));
                String module = root.path("name").asText(moduleOf(FactFileSupport.relative(repoRoot, file)));
                String sourcePath = FactFileSupport.relative(repoRoot, file);
                readDependencies(root.path("dependencies"), module, sourcePath, merged, "npm");
                readDependencies(root.path("devDependencies"), module, sourcePath, merged, "npm-dev");
            } catch (Exception e) {
                log.warn("解析 package.json 失败，跳过: {}", FactFileSupport.relative(repoRoot, file), e);
            }
        }
    }

    /**
     * 读取 npm 依赖节点。
     *
     * @param node       依赖节点
     * @param module     包名
     * @param sourcePath 来源路径
     * @param merged     聚合结果
     * @param type       依赖类型
     */
    private void readDependencies(JsonNode node, String module, String sourcePath,
                                  Map<String, Dependency> merged, String type) {
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry ->
                merge(merged, entry.getKey(), entry.getValue().asText(), type, module, sourcePath));
    }

    /**
     * 合并依赖（同一坐标保留首个版本，记录全部引用模块）。
     *
     * @param merged     聚合结果
     * @param key        坐标
     * @param version    版本
     * @param type       类型
     * @param module     模块
     * @param sourcePath 来源
     */
    private void merge(Map<String, Dependency> merged, String key, String version, String type,
                       String module, String sourcePath) {
        Dependency dependency = merged.computeIfAbsent(key, ignored -> new Dependency(version, type, sourcePath));
        if (dependency.version == null && version != null) {
            dependency.version = version;
        }
        dependency.modules.add(module);
        if (dependency.type == null) {
            dependency.type = type;
        }
    }

    /**
     * 解析 properties。
     *
     * @param text POM 文本
     * @return 属性表
     */
    private static Map<String, String> properties(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher blockMatcher = PROPERTIES.matcher(text);
        if (blockMatcher.find()) {
            Matcher tagMatcher = TAG.matcher(blockMatcher.group(1));
            while (tagMatcher.find()) {
                result.put(tagMatcher.group(1), tagMatcher.group(2).trim());
            }
        }
        String parent = parentBlock(text);
        String ownVersion = projectVersion(text);
        if (ownVersion == null && parent != null) {
            ownVersion = tag(parent, "version");
        }
        if (ownVersion != null) {
            result.putIfAbsent("project.version", ownVersion);
        }
        return result;
    }

    /**
     * 提取 {@code <parent>} 块原文。
     *
     * @param text POM 文本
     * @return parent 块；无则 {@code null}
     */
    private static String parentBlock(String text) {
        Matcher matcher = PARENT.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 提取项目自身版本（剔除 parent 与 dependency 块，避免误取父工程或依赖版本）。
     *
     * @param text POM 文本
     * @return 项目版本；无则 {@code null}
     */
    private static String projectVersion(String text) {
        String stripped = DEPENDENCY.matcher(PARENT.matcher(text).replaceAll("")).replaceAll("");
        Matcher matcher = PROJECT_VERSION.matcher(stripped);
        if (!matcher.find()) {
            return null;
        }
        String version = matcher.group(1).trim();
        return version.contains("${") ? null : version;
    }

    /**
     * 解析属性占位符。
     *
     * @param version    原始版本
     * @param properties 属性表
     * @return 解析后的版本
     */
    private static String resolve(String version, Map<String, String> properties) {
        if (version == null) {
            return null;
        }
        Matcher matcher = PROPERTY_REF.matcher(version);
        if (matcher.find()) {
            String resolved = properties.get(matcher.group(1));
            return resolved != null ? resolved : version;
        }
        return version;
    }

    /**
     * 读取 XML 标签值。
     *
     * @param block 依赖块
     * @param name  标签名
     * @return 标签值；无则 {@code null}
     */
    private static String tag(String block, String name) {
        Matcher matcher = Pattern.compile("<" + name + ">([^<]*)</" + name + ">").matcher(block);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /**
     * 从路径提取模块名。
     *
     * @param path 相对路径
     * @return 模块名
     */
    private static String moduleOf(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].startsWith("whale-") || parts[i].startsWith("whale-common")) {
                return parts[i];
            }
        }
        return parts.length > 1 ? parts[1] : "";
    }

    /**
     * 聚合中的依赖条目。
     */
    private static final class Dependency {

        /** 版本 */
        private String version;

        /** 依赖类型 */
        private String type;

        /** 首个来源路径 */
        private final String sourcePath;

        /** 引用模块 */
        private final Set<String> modules = new LinkedHashSet<>();

        /**
         * 构造依赖条目。
         *
         * @param version    版本
         * @param type       类型
         * @param sourcePath 来源路径
         */
        private Dependency(String version, String type, String sourcePath) {
            this.version = version;
            this.type = type;
            this.sourcePath = sourcePath;
        }
    }
}
