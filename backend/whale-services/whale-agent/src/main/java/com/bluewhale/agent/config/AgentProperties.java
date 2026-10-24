package com.bluewhale.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** 文本向量（embedding）配置 */
    private Embedding embedding = new Embedding();

    /** 模型配置 */
    private Llm llm = new Llm();

    /** 问答配置 */
    private Qa qa = new Qa();

    /** MCP / IDE 接入配置（轨 F） */
    private Mcp mcp = new Mcp();

    /** 只读数据查询配置（T14） */
    private DataQuery data = new DataQuery();

    /** 平台写操作配置（T15） */
    private Ops ops = new Ops();

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

        /** 是否启用向量通道（BM25 + 向量 RRF 融合） */
        private boolean vectorEnabled = true;

        /** 每个通道参与融合的候选条数（不足时按实际条数） */
        private int candidateTopK = 50;

        /** RRF 平滑常数 k（论文默认 60） */
        private int rrfK = 60;

        /** BM25 通道融合权重 */
        private double bm25Weight = 1.0D;

        /** 向量通道融合权重 */
        private double vectorWeight = 1.0D;
    }

    /**
     * 文本向量（embedding）参数。
     *
     * <p>{@code auto} 模式下配置了 API Key 时走 OpenAI 兼容 {@code /embeddings} 接口，
     * 否则降级为本地确定性哈希向量（无外部依赖、无出网），保证向量通道始终可用。</p>
     */
    @Data
    public static class Embedding {

        /** 是否启用向量化（关闭后索引不写向量，检索退回纯 BM25） */
        private boolean enabled = true;

        /** 提供方：auto / openai / hash / none */
        private String provider = "auto";

        /** API 基地址（含版本段）；为空时回退 {@code llm.base-url} */
        private String baseUrl;

        /** API Key（不入仓，通过环境变量注入）；为空时回退 {@code llm.api-key} */
        private String apiKey;

        /** 向量模型名 */
        private String model = "text-embedding-3-small";

        /** 向量维度：0 表示自动（本地哈希默认 256，远程模型以接口返回为准） */
        private int dimension;

        /** 请求超时（毫秒） */
        private int timeoutMillis = 20_000;

        /** 批量请求条数 */
        private int batchSize = 16;
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

        /** 问答引擎：{@code legacy}（Java 内置）或 {@code langchain}（ai/whale-ai 内网服务） */
        private String engine = "legacy";

        /** LangChain 引擎参数 */
        private Langchain langchain = new Langchain();

        /** 证据块最多条数 */
        private int evidenceTopK = 8;

        /** 单个证据块最大字符数 */
        private int maxChunkChars = 1200;

        /** 事实块最多条数 */
        private int maxFacts = 12;

        /** 携带的历史消息条数（user + assistant） */
        private int historyMessages = 6;

        /** 是否启用自主工具编排（配置模型后生效；关闭则退回单轮 RAG） */
        private boolean agenticEnabled = true;

        /** 自主编排最大工具轮数（硬上限 12） */
        private int maxToolSteps = 4;

        /**
         * 是否走 LangChain 引擎。
         *
         * @return {@code true} 表示委托 ai/whale-ai 流式问答
         */
        public boolean isLangchain() {
            return "langchain".equalsIgnoreCase(engine);
        }
    }

    /**
     * LangChain 引擎（ai/whale-ai）连接参数。
     */
    @Data
    public static class Langchain {

        /** 服务基地址（仅内网，不注册网关） */
        private String baseUrl = "http://127.0.0.1:8092";

        /** 流式问答总超时（秒） */
        private int timeoutSeconds = 120;
    }

    /**
     * 只读数据查询参数。
     *
     * <p>默认关闭；配置 {@code url} 后启用独立只读账号连接（仅授权白名单视图），
     * 不参与主数据源与 MyBatis 事务。</p>
     */
    @Data
    public static class DataQuery {

        /** 是否启用只读数据通道 */
        private boolean enabled = false;

        /** 只读账号 JDBC 地址（如 {@code jdbc:mysql://host:3306/bluewhale_agent?...}） */
        private String url;

        /** 只读账号 */
        private String username = "whale_ai_ro";

        /** 只读账号口令（环境变量注入，不入仓） */
        private String password;

        /** 允许访问的视图（库内名称，忽略大小写） */
        private List<String> allowedViews = new ArrayList<>(
                List.of("v_ai_agent_fact_summary", "v_ai_agent_usage_summary"));

        /** 单次返回行数上限 */
        private int maxRows = 200;

        /** 查询超时（秒） */
        private int timeoutSeconds = 5;
    }

    /**
     * MCP 接入参数。
     *
     * <p>默认关闭；仅在显式配置 {@code token} 后对外提供只读工具，
     * 供 Trae / IDE 等本地 MCP 客户端直连服务端口使用（不经网关）。</p>
     */
    @Data
    public static class Mcp {

        /** 是否启用 MCP 端点 */
        private boolean enabled = false;

        /** 本地只读 Token（不入仓，通过环境变量注入） */
        private String token;

        /** 单次检索最大条数 */
        private int maxSearchResults = 20;

        /** 单次读取文件最大行数 */
        private int maxReadLines = 400;

        /** 单次读取文件最大字符数 */
        private int maxReadChars = 20_000;
    }

    /**
     * 平台写操作参数（L2，人工确认后执行）。
     *
     * <p>默认关闭；开启后模型只能创建确认令牌，真正的写请求必须由用户确认触发。</p>
     */
    @Data
    public static class Ops {

        /** 是否启用平台写操作 */
        private boolean enabled = false;

        /** 确认令牌有效期（秒） */
        private int ttlSeconds = 300;

        /** 下游调用超时（秒） */
        private int timeoutSeconds = 10;

        /** 目标服务基地址（键为操作目录中的 target） */
        private Map<String, String> targets = defaultTargets();

        /**
         * 默认目标服务地址。
         *
         * @return 目标服务映射
         */
        private static Map<String, String> defaultTargets() {
            Map<String, String> targets = new LinkedHashMap<>();
            targets.put("form", "http://127.0.0.1:8084");
            return targets;
        }
    }
}
