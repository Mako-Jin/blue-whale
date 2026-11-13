package com.bluewhale.file.model.result;

/**
 * 个人存储配额结果。
 *
 * @param usedBytes 已用字节数
 * @param quotaBytes 生效配额字节数；不限时为 0
 * @param remainingBytes 剩余字节数
 * @param unlimited 是否不限配额
 * @author BlueWhale
 */
public record FileQuotaResult(long usedBytes, long quotaBytes, long remainingBytes, boolean unlimited) {
}
