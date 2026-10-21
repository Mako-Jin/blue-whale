package com.bluewhale.agent.support.ingest;

import com.bluewhale.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 仓库扫描器：遍历仓库、应用排除与敏感清单、计算内容哈希。
 *
 * <p>排除产物/依赖目录（{@code target}、{@code node_modules}、{@code dist}、{@code .git} 等）、
 * 锁文件与大文件；敏感文件（{@code .env*}、{@code *.pem}、{@code *.key}、{@code *credentials*}、
 * {@code application-prod*}）一律不入索引（Wave 5.47 安全约束）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepositoryScanner {

    /** 允许索引的扩展名（白名单，天然排除二进制） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "md", "java", "ts", "tsx", "js", "jsx", "vue", "sql",
            "yml", "yaml", "json", "xml", "properties");

    /** 排除目录名 */
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "target", "node_modules", "dist", "build", ".git", ".idea", ".vscode",
            "data", "logs", "coverage", ".mvn", "out");

    /** 排除文件名（锁文件与产物） */
    private static final Set<String> EXCLUDED_FILES = Set.of(
            "pnpm-lock.yaml", "package-lock.json", "yarn.lock", "npm-shrinkwrap.json");

    private final AgentProperties properties;

    private final ChunkerRegistry chunkerRegistry;

    /**
     * 扫描仓库根下的待索引文件（仅保留有对应切分器的文件）。
     *
     * @return 扫描结果列表
     */
    public List<ScannedFile> scan() {
        Path root = properties.resolveRepoRoot();
        List<ScannedFile> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root)
                            && EXCLUDED_DIRS.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && isEligible(root, file)) {
                        try {
                            files.add(toScannedFile(root, file));
                        } catch (IOException e) {
                            log.warn("扫描文件失败，跳过: {}", file, e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("访问文件失败，跳过: {} ({})", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("仓库扫描失败: " + root, e);
        }
        log.info("仓库扫描完成：root={}，命中文件数={}", root, files.size());
        return files;
    }

    /**
     * 判断文件是否可索引：路径排除、敏感清单、扩展名白名单、大小上限。
     *
     * @param root 仓库根
     * @param path 文件绝对路径
     * @return 可索引返回 {@code true}
     */
    boolean isEligible(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path segment : relative) {
            if (EXCLUDED_DIRS.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        String fileName = path.getFileName().toString();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (EXCLUDED_FILES.contains(lowerName) || isSensitive(lowerName)) {
            return false;
        }
        String extension = ChunkerRegistry.extension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return false;
        }
        if (chunkerRegistry.resolve(relative.toString().replace('\\', '/')) == null) {
            return false;
        }
        try {
            return Files.size(path) <= properties.getIngest().getMaxFileSize();
        } catch (IOException e) {
            log.warn("读取文件大小失败，跳过: {}", path, e);
            return false;
        }
    }

    /**
     * 敏感文件判定：命中即不入索引。
     *
     * @param lowerName 小写文件名
     * @return 敏感返回 {@code true}
     */
    private boolean isSensitive(String lowerName) {
        if (lowerName.startsWith(".env") || lowerName.endsWith(".pem") || lowerName.endsWith(".key")
                || lowerName.contains("credentials")) {
            return true;
        }
        return lowerName.startsWith("application-prod") || lowerName.startsWith("application-production");
    }

    /**
     * 构建扫描结果并计算 SHA-256。
     *
     * @param root 仓库根
     * @param path 文件绝对路径
     * @return 扫描结果
     * @throws IOException 读取失败
     */
    private ScannedFile toScannedFile(Path root, Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String relative = root.relativize(path).toString().replace('\\', '/');
        return new ScannedFile(relative, path, sha256(bytes), bytes.length);
    }

    /**
     * 计算 SHA-256 十六进制摘要。
     *
     * @param bytes 文件字节
     * @return 十六进制摘要
     */
    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] sum = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(sum.length * 2);
            for (byte value : sum) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 读取文件文本（UTF-8，解码失败按替换字符处理）。
     *
     * @param file 扫描结果
     * @return 文件全文
     */
    public String readText(ScannedFile file) {
        try {
            return new String(Files.readAllBytes(file.getAbsolutePath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败: " + file.getRelativePath(), e);
        }
    }
}
