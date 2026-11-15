package com.bluewhale.file.domain.storage;

/**
 * 存储介质中的对象（对账用只读投影）。
 *
 * @param bucket 桶名称
 * @param objectKey 对象键（统一以 {@code /} 分隔）
 * @param size 对象字节数
 * @author BlueWhale
 */
public record StorageObject(String bucket, String objectKey, long size) {
}
