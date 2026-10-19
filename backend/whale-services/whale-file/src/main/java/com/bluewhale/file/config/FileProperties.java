package com.bluewhale.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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

    /**
     * 本地存储配置项。
     *
     * @author BlueWhale
     */
    @Data
    public static class Storage {

        /** 存储类型：{@code local}（本地磁盘）或 {@code minio}（对象存储），默认 {@code local} */
        private String type = "local";

        /** 本地存储根目录，按 {@code ${root}/${bucket}/${objectKey}} 落盘 */
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
