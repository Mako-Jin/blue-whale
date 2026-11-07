package com.bluewhale.crm.commerce.domain.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * whale-contract 合同投影快照。
 *
 * @param contractId 合同服务合同 ID
 * @param contractNo 合同编号
 * @param title 合同标题
 * @param category 合同类别
 * @param amount 合同金额
 * @param currency 币种
 * @param status 合同状态
 * @param effectiveDate 生效日期
 * @param expireDate 到期日期
 * @param bizType 弱关联业务类型
 * @param bizId 弱关联业务 ID
 * @param ownerId 合同负责人
 * @param deptId 合同归属部门
 * @param updateTime 合同更新时间
 * @author BlueWhale
 */
public record ContractProjection(Long contractId,
                                 String contractNo,
                                 String title,
                                 String category,
                                 BigDecimal amount,
                                 String currency,
                                 String status,
                                 LocalDate effectiveDate,
                                 LocalDate expireDate,
                                 String bizType,
                                 String bizId,
                                 Long ownerId,
                                 Long deptId,
                                 LocalDateTime updateTime) {
}
