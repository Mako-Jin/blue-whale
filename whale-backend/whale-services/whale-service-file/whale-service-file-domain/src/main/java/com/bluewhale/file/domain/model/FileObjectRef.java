package com.bluewhale.file.domain.model;

/**
 * 元数据中的对象引用（对账用只读投影）。
 *
 * @param storageType 落盘时的存储插件类型，可为 {@code null}（历史数据）
 * @param bucket 桶名称
 * @param objectKey 对象键
 * @author BlueWhale
 */
public record FileObjectRef(String storageType, String bucket, String objectKey) {
}
