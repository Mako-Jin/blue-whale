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
 * @param contractFile  待签合同 PDF 快照（可为空，为空时由渠道侧按合同编号获取）
 * @param contractFileName 待签合同文件名
 * @author BlueWhale
 */
public record EsignTaskCommand(Long signTaskId, Long contractId, String contractCode, String contractTitle,
                               String signerName, String signerParty, byte[] contractFile,
                               String contractFileName) {
}
