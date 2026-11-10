package com.bluewhale.contract.esign.provider;

/**
 * 三方签署任务状态。
 *
 * @param status 状态：PENDING/SIGNING/SIGNED/REFUSED/EXPIRED
 * @param message 说明（可为空）
 * @author BlueWhale
 */
public record EsignTaskStatus(String status, String message) {
}
