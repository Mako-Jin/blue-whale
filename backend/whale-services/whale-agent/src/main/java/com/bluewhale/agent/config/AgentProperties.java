package com.bluewhale.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 知识 Agent 配置（{@code whale.agent.*}）。
 *
 * <p>仓库根与索引目录均支持显式配置；未配置时按启动目录向上探测仓库根
 * （同时存在 {@code backend/pom.xml} 与 {@code docs/} 的目录），索引目录回落
 * {@code <repo-root>/data/agent-index}，避免不同启动方式下路径漂移。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.agent")
public class AgentProperties {

    /** 仓库根目录（显式配置优先） */
    private String repoRoot;

    /** Lucene 索引目录（显式配置优先） */
    private String indexDir;

    /** 采集配置 */
    private Ingest ingest = new Ingest();

    /** 检索配置 */
    private Search search = new Search();

    /**
     * 解析仓库根目录：显式配置 → 向上探测 → 当前工作目录。
     *
     * @return 归一化后的绝对路径
     */
    public Path resolveRepoRoot() {
        if (StringUtils.hasText(repoRoot)) {
            return Paths.get(repoRoot).toAbsolutePath().normalize();
        }
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (Files.exists(cursor.resolve("backend/pom.xml")) && Files.isDirectory(cursor.resolve("docs"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    /**
     * 解析索引目录：显式配置 → {@code <repo-root>/data/agent-index}。
     *
     * @return 归一化后的绝对路径
     */
    public Path resolveIndexDir() {
        if (StringUtils.hasText(indexDir)) {
            return Paths.get(indexDir).toAbsolutePath().normalize();
        }
        return resolveRepoRoot().resolve("data/agent-index");
    }

    /**
     * 采集参数。
     */
    @Data
    public static class Ingest {

        /** 单文件上限（字节） */
        private long maxFileSize = 2L * 1024 * 1024;

        /** 增量扫描周期（秒），0 表示仅手动重建 */
        private int scanIntervalSeconds = 1800;

        /** 启动后是否立即构建索引 */
        private boolean buildOnStartup = true;
    }

    /**
     * 检索参数。
     */
    @Data
    public static class Search {

        /** 默认返回条数 */
        private int defaultTopK = 8;

        /** 最大返回条数 */
        private int maxTopK = 50;
    }
}
