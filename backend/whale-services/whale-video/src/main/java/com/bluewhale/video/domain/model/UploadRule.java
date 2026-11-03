package com.bluewhale.video.domain.model;

/**
 * 上传校验规则（按内容类型生效的单文件上限与时长上限）。
 *
 * @param maxBytes 单文件字节上限
 * @param maxDurationMs 时长上限（毫秒）
 * @author BlueWhale
 */
public record UploadRule(long maxBytes, long maxDurationMs) {
}
