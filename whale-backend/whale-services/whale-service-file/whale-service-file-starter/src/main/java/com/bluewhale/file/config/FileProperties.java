package com.bluewhale.file.config;

import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件服务配置项（前缀 {@code whale.file}）。
 *
 * <p>启动期校验：{@link #maxSizeBytes} 与 {@link #chunkSizeBytes} 必须为正数，
 * 配错时启动失败而不是静默拒绝所有上传。{@code quotaBytes} / {@code chunkUploadTtlHours}
 * 的 {@code <=0} 属于有效业务语义（不限配额 / 关闭清理），故不加下界约束。</p>
 *
 * @author BlueWhale
 */
@Data
@Validated
@ConfigurationProperties(prefix = "whale.file")
public class FileProperties {

    /** 本地存储配置 */
    private Storage storage = new Storage();

    /** 桶配置 */
    private Bucket bucket = new Bucket();

    /** 单文件最大字节数，默认 52428800（即 50MB）；超限抛 {@code PARAM_ERROR} */
    @Min(value = 1, message = "{validation.file.file-properties.max-size-bytes-min}")
    private long maxSizeBytes = 52428800L;

    /** 分片上传分片大小，默认 5242880（即 5MB） */
    @Min(value = 1, message = "{validation.file.file-properties.chunk-size-bytes-min}")
    private long chunkSizeBytes = 5242880L;

    /**
     * 分片上传会话保留时长（小时），默认 24；超过后由默认暂存实现在初始化新会话时限频清理，
     * 避免中途放弃或终态失败的暂存数据永久占用磁盘。{@code <=0} 表示关闭清理（{@code 0} 与负数等价）。
     */
    private long chunkUploadTtlHours = 24L;

    /** 单用户存储配额字节数，默认 1073741824（即 1GB）；{@code <=0} 表示不限 */
    private long quotaBytes = 1073741824L;

    /** 按权限码生效的上传策略（G1 大文件上传）：只放宽持有指定权限码的请求 */
    private UploadPolicy uploadPolicy = new UploadPolicy();

    /** 回收站与到期物理清理配置 */
    private Recycle recycle = new Recycle();

    /** 孤儿对象对账配置 */
    private Orphan orphan = new Orphan();

    /** 分享链接配置 */
    private Share share = new Share();

    /** 批量操作配置 */
    private Batch batch = new Batch();

    /** 上传内容安全配置 */
    private Security security = new Security();

    /** 租户级与桶级配额配置（个人配额见 {@link #quotaBytes}） */
    private Quota quota = new Quota();

    /** CDN 加速配置 */
    private Cdn cdn = new Cdn();

    /** 内容访问防护配置（防盗链与限流） */
    private Access access = new Access();

    /** 版本保留配置 */
    private Version version = new Version();

    /**
     * 上传策略配置项：按网关可信注入的 {@code X-Permission-Codes} 选择生效上限与配额，
     * 未命中任何规则时回落 {@code max-size-bytes} / {@code quota-bytes} 全局默认值。
     *
     * @author BlueWhale
     */
    @Data
    public static class UploadPolicy {

        /**
         * 是否启用按权限码策略。
         *
         * <p>关闭后所有上传直接使用全局默认上限与配额（不遍历规则）；开启但当前请求未命中
         * 任何规则时同样回落全局默认值。两者结果一致、语义不同：前者是「策略被显式关闭」，
         * 后者是「策略启用但调用者无对应特权」。</p>
         */
        private boolean enabled = true;

        /**
         * 权限码规则集合；同一请求命中多条时取单文件上限最大的一条。
         *
         * <p>内置默认规则由 {@link #createDefaultRules()} 提供。配置文件中显式声明该列表会
         * <b>整体覆盖</b>内置默认规则（Spring Boot 按配置重建列表），需要保留内置规则时
         * 必须在配置中列出全部规则；配置为空列表即表示不使用任何按权限码放行的大文件策略。</p>
         */
        private List<Rule> rules = createDefaultRules();

        /**
         * 构造内置默认规则：视频 2GB / 8GB、电影 4GB / 不限、音乐 100MB / 沿用默认配额。
         *
         * @return 默认规则列表
         */
        private static List<Rule> createDefaultRules() {
            List<Rule> defaults = new ArrayList<>();
            defaults.add(Rule.of("sys:video:upload", 2147483648L, 8589934592L));
            defaults.add(Rule.of("sys:video:movie:manage", 4294967296L, 0L));
            defaults.add(Rule.of("sys:music:upload", 104857600L, null));
            return defaults;
        }
    }

    /**
     * 单条上传策略规则：命中权限码后生效的单文件上限与个人配额。
     *
     * @author BlueWhale
     */
    @Data
    public static class Rule {

        /** 需要的权限码（网关注入，客户端伪造头在网关侧被剥离） */
        private String permission;

        /**
         * 生效的单文件最大字节数；{@code <=0} 表示该规则在解析阶段被跳过
         * （用于临时停用某条规则），不会导致启动失败
         */
        private long maxSizeBytes;

        /**
         * 生效的单用户存储配额字节数：{@code null} 表示沿用 {@code whale.file.quota-bytes}，
         * {@code <=0} 表示不限配额。
         *
         * <p>YAML 中不写该键（或留空）绑定为 {@code null}，写 {@code 0} 绑定为 {@code 0}，
         * 两者语义不同：前者跟随全局配额，后者不限配额，配置时需显式区分。</p>
         */
        private Long quotaBytes;

        /**
         * 构造规则（用于内置默认值与单元测试）
         *
         * @param permission 需要的权限码
         * @param maxSizeBytes 生效的单文件最大字节数
         * @param quotaBytes 生效的个人配额；{@code null} 表示沿用全局默认配额
         * @return 规则实例
         */
        public static Rule of(String permission, long maxSizeBytes, Long quotaBytes) {
            Rule rule = new Rule();
            rule.setPermission(permission);
            rule.setMaxSizeBytes(maxSizeBytes);
            rule.setQuotaBytes(quotaBytes);
            return rule;
        }
    }

    /**
     * 存储配置项（与插件注册表共用前缀 {@code whale.file.storage}）。
     *
     * @author BlueWhale
     */
    @Data
    public static class Storage {

        /** 活动存储插件类型：{@code local}（本地磁盘）或 {@code minio}（对象存储），默认 {@code local} */
        private String type = "local";

        /**
         * 本地插件根目录及分片临时目录根，按 {@code ${root}/${bucket}/${objectKey}} 落盘；
         * 默认相对进程工作目录，跨平台可用，生产环境应显式配置绝对路径。
         *
         * <p>与本地插件模块的 {@code LocalStorageProperties.root} 绑定同一个配置键
         * {@code whale.file.storage.root}（配置层与插件层不可互相依赖，故各保留一份声明），
         * <b>修改默认值时必须两处同步</b>。</p>
         */
        private String root = "./data/whale-file";

        /** 落盘加密配置 */
        private Encryption encryption = new Encryption();
    }

    /**
     * 落盘加密配置项（前缀 {@code whale.file.storage.encryption}）。
     *
     * <p>启用后新写入对象使用 AES-GCM 加密，读取时按对象头魔数自动识别并解密；
     * 存量明文对象不受影响。密钥必须是 Base64 编码的 16/24/32 字节，启用时缺失或非法
     * 会导致启动失败。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Encryption {

        /** 是否启用落盘加密；默认关闭 */
        private boolean enabled = false;

        /** Base64 编码的 AES 密钥（16/24/32 字节）；启用加密时必填 */
        private String key = "";
    }

    /**
     * 回收站配置项（前缀 {@code whale.file.recycle}）。
     *
     * <p>到期清理任务由 {@code @ScheduledJob(cron = "${whale.file.recycle.purge-cron:0 30 3 * * ?}")}
     * 驱动，因此 cron 只从环境绑定；本类承载开关、保留天数与批大小。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Recycle {

        /** 是否启用到期物理清理；关闭后任务空跑 */
        private boolean purgeEnabled = true;

        /** 逻辑删除后的保留天数，默认 30；{@code <=0} 表示不自动清理（无限期保留） */
        private long retentionDays = 30L;

        /** 单批清理条数，默认 200；避免一次扫描过多记录长时间占用连接 */
        @Min(value = 1, message = "{validation.file.recycle.purge-batch-size-min}")
        private int purgeBatchSize = 200;
    }

    /**
     * 孤儿对象对账配置项（前缀 {@code whale.file.orphan}）。
     *
     * <p>默认关闭：全量列举存储对象与元数据引用，量级较大，需按环境评估后开启。
     * 对账只统计「存储有、元数据无」的孤儿对象与「元数据有、存储无」的悬空元数据；
     * 默认只报告，只有显式开启 {@code delete-orphan-objects} 才删除孤儿对象，
     * 悬空元数据永不自动删除。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Orphan {

        /** 是否启用对账任务；默认关闭 */
        private boolean enabled = false;

        /** 是否删除孤儿对象；默认关闭（仅报告） */
        private boolean deleteOrphanObjects = false;

        /** 日志中每类差异最多打印的样例行数 */
        @Min(value = 0, message = "{validation.file.orphan.report-samples-min}")
        private int reportSamples = 20;
    }

    /**
     * 分享链接配置项（前缀 {@code whale.file.share}）。
     *
     * <p>有效期只做上限截断，避免创建「几乎永久」的公开链接；下载次数与密码由创建请求决定，
     * 不设全局默认次数上限（{@code 0} 表示不限）。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Share {

        /** 未指定有效期时的默认值（小时），默认 168（7 天） */
        @Min(value = 1, message = "{validation.file.share.default-expire-hours-min}")
        private long defaultExpireHours = 168L;

        /** 有效期上限（小时），默认 720（30 天），超出截断 */
        @Min(value = 1, message = "{validation.file.share.max-expire-hours-min}")
        private long maxExpireHours = 720L;

        /**
         * 是否启用到期分享清理；默认开启。
         *
         * <p>由 {@code ShareCleanupTask} 按 cron（{@code whale.file.share.purge-cron}）物理删除
         * 到期超过保留期的分享记录，避免失效链接无限累积。</p>
         */
        private boolean purgeEnabled = true;

        /**
         * 到期后的额外保留天数，默认 7；{@code <=0} 表示到期即删。
         *
         * <p>保留一段时间便于短期追溯失效链接的创建与使用记录。</p>
         */
        private long retentionDays = 7L;

        /** 单批删除条数，默认 200；避免一次删除过多行造成长事务 */
        @Min(value = 1, message = "{validation.file.share.purge-batch-size-min}")
        private int purgeBatchSize = 200;
    }

    /**
     * 批量操作配置项（前缀 {@code whale.file.batch}）。
     *
     * <p>批量删除与打包下载共用文件数上限，避免单请求承担过大的数据库往返与响应体；
     * 打包体积只限制未压缩总字节数，超出上限整体拒绝而不是截断，避免产出残缺压缩包。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Batch {

        /** 单次批量删除/打包的最大文件数，默认 100 */
        @Min(value = 1, message = "{validation.file.batch.max-files-min}")
        private int maxFiles = 100;

        /** 打包下载的未压缩总字节上限，默认 4294967296（即 4GB） */
        @Min(value = 1, message = "{validation.file.batch.max-zip-bytes-min}")
        private long maxZipBytes = 4294967296L;
    }

    /**
     * 上传内容安全配置项（前缀 {@code whale.file.security}）。
     *
     * <p>按文件头魔数识别真实内容类型：识别为禁止类型（默认 {@code EXE}/{@code ELF}/{@code MACHO}）
     * 直接拒绝；扩展名有映射但内容类型不符时拒绝；无法识别一律放行。关闭开关即整体回退到
     * 仅按扩展名与大小校验的旧行为。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Security {

        /** 是否启用魔数校验；关闭后不读取文件头 */
        private boolean magicEnabled = true;

        /**
         * 禁止上传的内容类型名（{@code FileMagicType} 常量名，大小写不敏感），默认拦截三类可执行文件。
         *
         * <p>配置文件显式声明该列表会整体覆盖默认值；空列表表示不禁用任何类型。</p>
         */
        private List<String> blockedTypes = new ArrayList<>(List.of("EXE", "ELF", "MACHO"));
    }

    /**
     * 租户级与桶级配额配置项（前缀 {@code whale.file.quota}）。
     *
     * <p>与个人配额 {@code whale.file.quota-bytes} 一起组成三个校验维度：个人按上传者统计，
     * 租户按租户内正常文件总量统计，桶按租户内单个桶的字节数统计。默认均为 0（不限），
     * 升级后不改变既有行为；桶配额只在存在租户上下文时生效。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Quota {

        /** 租户级配额字节数，默认 0；{@code <=0} 表示不限 */
        private long tenantBytes = 0L;

        /** 单桶配额字节数（租户内计量），默认 0；{@code <=0} 表示不限 */
        private long bucketBytes = 0L;
    }

    /**
     * CDN 加速配置项（前缀 {@code whale.file.cdn}）。
     *
     * <p>只重写预签名下载 URL 的 {@code scheme://host[:port]}，路径与查询串原样保留：
     * S3/OSS 预签名不把 host 纳入签名，替换域名后仍可校验，但要求 CDN 回源到对象存储
     * 且不改写查询串。空白表示不启用，行为与未引入本配置时完全一致。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Cdn {

        /**
         * CDN 加速地址（含 scheme），例如 {@code https://cdn.example.com}。
         *
         * <p>建议不带路径与结尾斜杠；配置路径时会被拼在对象路径之前。</p>
         */
        private String baseUrl = "";
    }

    /**
     * 桶配置项。
     *
     * @author BlueWhale
     */
    @Data
    public static class Bucket {

        /**
         * 默认桶名，只通过 {@code whale.file.bucket.default} 绑定。
         *
         * <p>字段名带 {@code Value} 后缀是为避开 Java 关键字 {@code default}。字段级
         * {@code @Getter(NONE)} / {@code @Setter(NONE)} 抑制 Lombok 生成
         * {@code getDefaultValue} / {@code setDefaultValue}，避免
         * {@code whale.file.bucket.default} 与 {@code whale.file.bucket.default-value}
         * 两个键同时可写导致的绑定歧义；读写入口仅保留下面手写的
         * {@link #getDefault()} / {@link #setDefault(String)}。</p>
         */
        @Getter(AccessLevel.NONE)
        @Setter(AccessLevel.NONE)
        private String defaultValue = "bluewhale";

        /**
         * 读取默认桶名（配置键 {@code whale.file.bucket.default}）。
         *
         * @return 默认桶名
         */
        public String getDefault() {
            return defaultValue;
        }

        /**
         * 写入默认桶名（配置键 {@code whale.file.bucket.default}）。
         *
         * @param defaultBucket 默认桶名
         */
        public void setDefault(String defaultBucket) {
            this.defaultValue = defaultBucket;
        }
    }

    /**
     * 版本保留配置项（前缀 {@code whale.file.version}）。
     *
     * @author BlueWhale
     */
    @Data
    public static class Version {

        /**
         * 每个文件保留的版本总数上限（含根版本），默认 20；{@code <=0} 表示不限。
         *
         * <p>新版本产生后按版本号倒序清理超出的最旧版本；根版本永不清理，
         * 配置为 1 等价于不保留历史版本。</p>
         */
        @Min(value = 0, message = "{validation.file.version.max-versions-min}")
        private int maxVersions = 20;

        /**
         * 是否启用存量历史版本定时清理；默认关闭。
         *
         * <p>开启后由 {@code VersionCleanupTask} 按 cron 扫描全部文件并清理超出上限的旧版本，
         * 用于批次 18 上线前已存在的历史数据。</p>
         */
        private boolean cleanupEnabled = false;

        /** 定时清理单批扫描的根文件数量，默认 100 */
        @Min(value = 1, message = "{validation.file.version.cleanup-batch-size-min}")
        private int cleanupBatchSize = 100;
    }

    /**
     * 内容访问防护配置项（前缀 {@code whale.file.access}）。
     *
     * <p>只作用于下载与预览等文件内容接口；默认全部关闭，升级后不改变既有行为。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Access {

        /** 防盗链配置 */
        private Hotlink hotlink = new Hotlink();

        /** 限流配置 */
        private RateLimit rateLimit = new RateLimit();
    }

    /**
     * 防盗链配置项（前缀 {@code whale.file.access.hotlink}）。
     *
     * <p>按 {@code Referer} 主机名判断来源：允许同主机、精确主机或通配后缀
     * （如 {@code *.example.com}）。{@code allow-empty-referer} 控制直接访问
     * （浏览器地址栏、客户端不带 Referer）是否放行。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class Hotlink {

        /** 是否启用防盗链；默认关闭 */
        private boolean enabled = false;

        /** 允许的来源主机列表，支持精确主机与 {@code *.suffix} 通配后缀 */
        private List<String> allowedHosts = new ArrayList<>();

        /** 无 Referer 时是否放行；默认放行，便于直接访问与客户端下载 */
        private boolean allowEmptyReferer = true;
    }

    /**
     * 内容接口限流配置项（前缀 {@code whale.file.access.rate-limit}）。
     *
     * <p>按调用者（{@code X-User-Id}，缺失时按客户端 IP）使用令牌桶限流；
     * 进程内实现，多实例部署时各自独立计数。</p>
     *
     * @author BlueWhale
     */
    @Data
    public static class RateLimit {

        /** 是否启用限流；默认关闭 */
        private boolean enabled = false;

        /** 每秒补充的令牌数（稳态 QPS），默认 20 */
        @Min(value = 1, message = "{validation.file.rate-limit.requests-per-second-min}")
        private int requestsPerSecond = 20;

        /** 桶容量（允许的瞬时突发数），默认 40 */
        @Min(value = 1, message = "{validation.file.rate-limit.burst-min}")
        private int burst = 40;
    }
}
