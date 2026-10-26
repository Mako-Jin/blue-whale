package com.bluewhale.contract.esign.provider;

/**
 * 创建三方签署任务结果。
 *
 * @param providerTaskId 三方任务号
 * @param signUrl        签署链接（本地渠道为 {@code null}）
 * @author BlueWhale
 */
public record EsignTaskResult(String providerTaskId, String signUrl) {
}
