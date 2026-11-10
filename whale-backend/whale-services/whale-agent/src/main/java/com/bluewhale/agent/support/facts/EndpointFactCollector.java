package com.bluewhale.agent.support.facts;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接口事实采集器：扫描后端 {@code controller} 包中的映射注解。
 *
 * <p>产出 {@code ENDPOINT} 事实（{@code METHOD 路径}），值为控制器、方法名与所需权限码，
 * 用于「某功能对应哪个接口 / 需要什么权限」类问题。运行期 OpenAPI 未启用时同样可用。</p>
 *
 * @author BlueWhale
 */
@Component
public class EndpointFactCollector implements FactCollector {

    /** 方法级映射注解 */
    private static final Pattern MAPPING = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping");

    /** 权限注解 */
    private static final Pattern PERMISSION = Pattern.compile("@RequirePermission\\s*\\(\\s*\"([^\"]+)\"");

    /** 引号内容 */
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    /** 方法声明 */
    private static final Pattern METHOD_DECL = Pattern.compile(
            "^\\s*(?:public|protected|private)\\s+[\\w<>,\\[\\]\\s.?]+\\s+(\\w+)\\s*\\(");

    /** 类声明 */
    private static final Pattern CLASS_DECL = Pattern.compile("\\b(class|interface|record|enum)\\s+(\\w+)");

    @Override
    public List<FactRecord> collect(Path repoRoot) {
        List<FactRecord> facts = new ArrayList<>();
        List<Path> files = FactFileSupport.walk(repoRoot.resolve("whale-backend"),
                path -> path.getFileName().toString().endsWith(".java")
                        && path.toString().replace('\\', '/').contains("/controller/"));
        for (Path file : files) {
            collectFile(repoRoot, file, facts);
        }
        return facts;
    }

    /**
     * 解析单个控制器。
     *
     * @param repoRoot 仓库根
     * @param file     控制器文件
     * @param facts    事实容器
     */
    private void collectFile(Path repoRoot, Path file, List<FactRecord> facts) {
        String text = FactFileSupport.read(file);
        if (text.isEmpty()) {
            return;
        }
        String sourcePath = FactFileSupport.relative(repoRoot, file);
        String module = moduleOf(sourcePath);
        String base = "";
        String controller = file.getFileName().toString().replace(".java", "");
        boolean classDeclared = false;
        List<String> pendingMethods = new ArrayList<>();
        List<String> pendingPaths = new ArrayList<>();
        String pendingPermission = null;
        for (String line : text.lines().toList()) {
            if (!classDeclared) {
                Matcher classMatcher = CLASS_DECL.matcher(line);
                if (classMatcher.find()) {
                    classDeclared = true;
                    controller = classMatcher.group(2);
                } else if (line.contains("@RequestMapping")) {
                    base = firstQuoted(line);
                }
            }
            Matcher permissionMatcher = PERMISSION.matcher(line);
            if (permissionMatcher.find()) {
                pendingPermission = permissionMatcher.group(1);
            }
            Matcher mappingMatcher = MAPPING.matcher(line);
            while (mappingMatcher.find()) {
                pendingMethods.add(mappingMatcher.group(1).toUpperCase());
                List<String> paths = quotedAll(line);
                pendingPaths.add(paths.isEmpty() ? "" : paths.get(0));
            }
            Matcher methodMatcher = METHOD_DECL.matcher(line);
            if (methodMatcher.find() && !pendingMethods.isEmpty()) {
                for (int i = 0; i < pendingMethods.size(); i++) {
                    String path = normalize(base, i < pendingPaths.size() ? pendingPaths.get(i) : "");
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("module", module);
                    value.put("controller", controller);
                    value.put("method", methodMatcher.group(1));
                    value.put("permission", pendingPermission == null ? "" : pendingPermission);
                    facts.add(new FactRecord("ENDPOINT",
                            pendingMethods.get(i) + " " + path, value, sourcePath));
                }
                pendingMethods = new ArrayList<>();
                pendingPaths = new ArrayList<>();
                pendingPermission = null;
            }
        }
    }

    /**
     * 拼接类级与方法级路径。
     *
     * @param base 类级前缀
     * @param path 方法级路径
     * @return 归一化路径
     */
    private static String normalize(String base, String path) {
        String left = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String right = path.isEmpty() || path.startsWith("/") ? path : "/" + path;
        String result = left + right;
        return result.isEmpty() ? "/" : result;
    }

    /**
     * 提取首个引号内容。
     *
     * @param line 行内容
     * @return 内容；无则空串
     */
    private static String firstQuoted(String line) {
        List<String> values = quotedAll(line);
        return values.isEmpty() ? "" : values.get(0);
    }

    /**
     * 提取全部引号内容。
     *
     * @param line 行内容
     * @return 内容列表
     */
    private static List<String> quotedAll(String line) {
        List<String> result = new ArrayList<>();
        Matcher matcher = QUOTED.matcher(line);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    /**
     * 从路径提取模块名。
     *
     * @param path 相对路径
     * @return 模块名
     */
    private static String moduleOf(String path) {
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.startsWith("whale-")) {
                return part;
            }
        }
        return "";
    }
}
