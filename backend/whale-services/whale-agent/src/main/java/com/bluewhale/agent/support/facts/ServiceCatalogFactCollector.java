package com.bluewhale.agent.support.facts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务清单与网关路由事实采集器。
 *
 * <p>来源一：{@code docs/service-catalog.md} 服务表 → {@code SERVICE} / {@code PORT} 事实；
 * 来源二：网关 {@code application.yml} 的静态路由 → {@code ROUTE} 事实。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class ServiceCatalogFactCollector implements FactCollector {

    /** 服务清单文档 */
    private static final String CATALOG_PATH = "docs/service-catalog.md";

    /** 网关配置文件 */
    private static final String GATEWAY_CONFIG =
            "backend/whale-services/whale-gateway/src/main/resources/application.yml";

    /** 反引号内容 */
    private static final Pattern BACKTICK = Pattern.compile("`([^`]+)`");

    /** 端口数字 */
    private static final Pattern PORT = Pattern.compile("\\b(\\d{2,5})\\b");

    /** 数据库名 */
    private static final Pattern DATABASE = Pattern.compile("bluewhale_[a-z0-9_]+");

    /** 对外一级前缀 */
    private static final Pattern PREFIX = Pattern.compile("/(?:[a-z*][a-z0-9_*\\-]*)");

    @Override
    public List<FactRecord> collect(Path repoRoot) {
        List<FactRecord> facts = new ArrayList<>();
        facts.addAll(collectServices(repoRoot));
        facts.addAll(collectRoutes(repoRoot));
        return facts;
    }

    /**
     * 解析服务清单表格。
     *
     * @param repoRoot 仓库根
     * @return SERVICE / PORT 事实
     */
    private List<FactRecord> collectServices(Path repoRoot) {
        Path file = repoRoot.resolve(CATALOG_PATH);
        List<FactRecord> facts = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return facts;
        }
        List<String> lines = FactFileSupport.read(file).lines().toList();
        for (String line : lines) {
            if (!line.startsWith("|")) {
                continue;
            }
            String[] cells = line.split("\\|", -1);
            if (cells.length < 11) {
                continue;
            }
            String service = firstBacktick(cells[2]);
            if (service == null || !service.startsWith("whale-")) {
                continue;
            }
            String port = extractPort(cells[5]);
            Map<String, Object> serviceValue = new LinkedHashMap<>();
            serviceValue.put("group", clean(cells[3]));
            serviceValue.put("module", firstBacktick(cells[4]));
            serviceValue.put("port", port);
            serviceValue.put("prefix", extractPrefix(cells[6]));
            serviceValue.put("gatewayRoute", extractRoute(cells[7]));
            serviceValue.put("database", extractDatabase(cells[8]));
            serviceValue.put("application", firstBacktick(cells[9]));
            serviceValue.put("responsibility", clean(cells[10]));
            serviceValue.put("status", cells.length > 11 ? clean(cells[11]) : "");
            facts.add(new FactRecord("SERVICE", service, serviceValue, CATALOG_PATH));
            if (port != null) {
                Map<String, Object> portValue = new LinkedHashMap<>();
                portValue.put("port", Integer.parseInt(port));
                portValue.put("service", service);
                portValue.put("module", firstBacktick(cells[4]));
                facts.add(new FactRecord("PORT", service, portValue, CATALOG_PATH));
            }
        }
        return facts;
    }

    /**
     * 解析网关静态路由。
     *
     * @param repoRoot 仓库根
     * @return ROUTE 事实
     */
    @SuppressWarnings("unchecked")
    private List<FactRecord> collectRoutes(Path repoRoot) {
        Path file = repoRoot.resolve(GATEWAY_CONFIG);
        List<FactRecord> facts = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return facts;
        }
        try {
            Map<String, Object> root = new Yaml().load(FactFileSupport.read(file));
            Map<String, Object> spring = child(root, "spring");
            Map<String, Object> cloud = child(spring, "cloud");
            Map<String, Object> gateway = child(cloud, "gateway");
            Map<String, Object> server = child(gateway, "server");
            Map<String, Object> webflux = child(server, "webflux");
            Object routes = webflux == null ? null : webflux.get("routes");
            if (!(routes instanceof List<?> list)) {
                return facts;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> route)) {
                    continue;
                }
                Object id = route.get("id");
                if (id == null) {
                    continue;
                }
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("uri", String.valueOf(route.get("uri")));
                value.put("predicates", predicates(route.get("predicates")));
                value.put("filters", route.get("filters"));
                facts.add(new FactRecord("ROUTE", String.valueOf(id), value, GATEWAY_CONFIG));
            }
        } catch (RuntimeException e) {
            log.warn("解析网关路由配置失败，跳过 ROUTE 事实采集", e);
        }
        return facts;
    }

    /**
     * 读取嵌套 Map。
     *
     * @param parent 父 Map
     * @param key    键
     * @return 子 Map；不存在返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> parent, String key) {
        if (parent == null) {
            return null;
        }
        Object value = parent.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /**
     * 归一化断言列表。
     *
     * @param raw 原始断言
     * @return 断言字符串列表
     */
    private static List<String> predicates(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    /**
     * 提取第一个反引号内容。
     *
     * @param cell 表格单元
     * @return 内容；无则 {@code null}
     */
    private static String firstBacktick(String cell) {
        Matcher matcher = BACKTICK.matcher(cell);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /**
     * 提取端口。
     *
     * @param cell 表格单元
     * @return 端口字符串；无则 {@code null}
     */
    private static String extractPort(String cell) {
        Matcher matcher = PORT.matcher(cell.replace("*", "").replace("——", ""));
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 提取对外一级前缀。
     *
     * @param cell 表格单元
     * @return 前缀；无则空串
     */
    private static String extractPrefix(String cell) {
        Matcher matcher = PREFIX.matcher(cell);
        return matcher.find() ? matcher.group() : "";
    }

    /**
     * 提取网关目标地址。
     *
     * @param cell 表格单元
     * @return {@code lb://服务名}；无则空串
     */
    private static String extractRoute(String cell) {
        int index = cell.indexOf("lb://");
        if (index < 0) {
            return "";
        }
        String tail = cell.substring(index);
        Matcher matcher = Pattern.compile("lb://[A-Za-z0-9_\\-]+").matcher(tail);
        return matcher.find() ? matcher.group() : "";
    }

    /**
     * 提取数据库名。
     *
     * @param cell 表格单元
     * @return 数据库名；无则空串
     */
    private static String extractDatabase(String cell) {
        Matcher matcher = DATABASE.matcher(cell);
        return matcher.find() ? matcher.group() : "";
    }

    /**
     * 清理表格单元中的 Markdown 修饰。
     *
     * @param cell 表格单元
     * @return 纯文本
     */
    private static String clean(String cell) {
        return cell.replace("**", "").replace("`", "").replace("~~", "").trim();
    }
}
