package com.bluewhale.agent.support.facts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 事实采集的文件工具：遍历、读取与相对路径归一。
 *
 * @author BlueWhale
 */
public final class FactFileSupport {

    /** 遍历时跳过的目录 */
    private static final Set<String> SKIP_DIRS = Set.of(
            "target", "node_modules", "dist", "build", "out", ".git", ".idea",
            ".vscode", "data", "logs", "coverage", ".mvn");

    private FactFileSupport() {
    }

    /**
     * 递归遍历目录，返回命中过滤条件的普通文件。
     *
     * @param root   遍历起点
     * @param filter 过滤条件
     * @return 文件列表
     */
    public static List<Path> walk(Path root, Predicate<Path> filter) {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return result;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && SKIP_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && filter.test(file)) {
                        result.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return result;
        }
        return result;
    }

    /**
     * 读取文件文本（UTF-8）。
     *
     * @param path 文件路径
     * @return 文本内容；读取失败返回空串
     */
    public static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 仓库相对路径（统一使用 {@code /} 分隔）。
     *
     * @param repoRoot 仓库根
     * @param path     文件路径
     * @return 相对路径
     */
    public static String relative(Path repoRoot, Path path) {
        try {
            return repoRoot.relativize(path).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return path.toString().replace('\\', '/');
        }
    }

}
