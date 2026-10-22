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

    /** 模型配置 */
    private Llm llm = new Llm();

    /** 问答配置 */
    private Qa qa = new Qa();

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

    /**
     * OpenAI 兼容模型参数（未配置 api-key 时自动降级为检索摘要）。
     */
    @Data
    public static class Llm {

        /** 是否启用模型（关闭后始终走检索摘要） */
        private boolean enabled = true;

        /** API 基地址（含版本段，如 {@code https://api.openai.com/v1}） */
        private String baseUrl = "https://api.openai.com/v1";

        /** API Key（不入仓，通过环境变量注入） */
        private String apiKey;

        /** 对话模型名 */
        private String model = "gpt-4o-mini";

        /** 请求超时（毫秒） */
        private int timeoutMillis = 60_000;

        /** 单次回答最大 token */
        private int maxTokens = 1024;

        /** 采样温度 */
        private double temperature = 0.2D;
    }

    /**
     * 问答编排参数。
     */
    @Data
    public static class Qa {

        /** 证据块最多条数 */
        private int evidenceTopK = 8;

        /** 单个证据块最大字符数 */
        private int maxChunkChars = 1200;

        /** 事实块最多条数 */
        private int maxFacts = 12;

        /** 携带的历史消息条数（user + assistant） */
        private int historyMessages = 6;
    }
}
