package com.bluewhale.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件服务配置项（前缀 {@code whale.file}）。
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.file")
public class FileProperties {

    /** 本地存储配置 */
    private Storage storage = new Storage();

    /** 桶配置 */
    private Bucket bucket = new Bucket();

    /** 单文件最大字节数，默认 52428800（即 50MB）；超限抛 {@code PARAM_ERROR} */
    private long maxSizeBytes = 52428800L;

    /** 分片上传分片大小，默认 5242880（即 5MB） */
    private long chunkSizeBytes = 5242880L;

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

        /** 是否启用按权限码策略；关闭后所有上传均使用全局默认上限与配额 */
        private boolean enabled = true;

        /** 权限码规则集合；同一请求命中多条时取单文件上限最大的一条 */
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

        /** 生效的单文件最大字节数；{@code <=0} 的规则在解析时被忽略 */
        private long maxSizeBytes;

        /**
         * 生效的单用户存储配额字节数：{@code null} 表示沿用 {@code whale.file.quota-bytes}，
         * {@code <=0} 表示不限配额
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

        /** 本地插件根目录及分片临时目录根，按 {@code ${root}/${bucket}/${objectKey}} 落盘 */
        private String root = "D:/data/whale-file";
    }

    /**
     * 桶配置项。
     *
     * @author BlueWhale
     */
    @Data
    public static class Bucket {

        /** 默认桶名 */
        private String defaultValue = "bluewhale";

        /**
         * 读取默认桶名（兼容配置键 {@code whale.file.bucket.default}）。
         *
         * @return 默认桶名
         */
        public String getDefault() {
            return defaultValue;
        }

        /**
         * 写入默认桶名（兼容配置键 {@code whale.file.bucket.default}）。
         *
         * @param defaultBucket 默认桶名
         */
        public void setDefault(String defaultBucket) {
            this.defaultValue = defaultBucket;
        }
    }
}
