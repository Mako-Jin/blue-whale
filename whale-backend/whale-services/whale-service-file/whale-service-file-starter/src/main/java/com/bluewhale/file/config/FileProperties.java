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
    @Min(value = 1, message = "whale.file.max-size-bytes 必须大于 0")
    private long maxSizeBytes = 52428800L;

    /** 分片上传分片大小，默认 5242880（即 5MB） */
    @Min(value = 1, message = "whale.file.chunk-size-bytes 必须大于 0")
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
}
