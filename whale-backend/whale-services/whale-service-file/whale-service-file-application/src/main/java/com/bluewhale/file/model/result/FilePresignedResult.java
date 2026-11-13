package com.bluewhale.file.model.result;

/**
 * 预签名下载结果。
 *
 * @param url 预签名 URL
 * @param expiresSeconds 生效有效期（秒）
 * @author BlueWhale
 */
public record FilePresignedResult(String url, Integer expiresSeconds) {
}
