package com.bluewhale.contract.esign.provider;

/**
 * 创建三方签署任务所需上下文。
 *
 * @param signTaskId    平台签署任务ID
 * @param contractId    合同ID
 * @param contractCode  合同编号
 * @param contractTitle 合同标题
 * @param signerName    签署人姓名
 * @param signerParty   签署方（A/B/OTHER）
 * @author BlueWhale
 */
public record EsignTaskCommand(Long signTaskId, Long contractId, String contractCode, String contractTitle,
                               String signerName, String signerParty) {
}
