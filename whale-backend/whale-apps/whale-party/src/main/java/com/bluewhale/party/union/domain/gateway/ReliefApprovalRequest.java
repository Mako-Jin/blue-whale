package com.bluewhale.party.union.domain.gateway;

import com.bluewhale.party.union.domain.model.ReliefType;

import java.math.BigDecimal;

/**
 * 慰问帮扶审批发起请求。
 *
 * <p>只带审批选路与展示需要的业务事实，不透传整个申请对象：审批侧不需要知道备注、
 * 提示语等本服务内部字段，也避免两边实体在编译期绑死。</p>
 *
 * @param reliefId 申请 ID
 * @param reliefNo 申请编号
 * @param unionMemberId 工会会员 ID
 * @param reliefType 慰问类型
 * @param applyYear 申请年度
 * @param amount 申请金额
 * @author BlueWhale
 */
public record ReliefApprovalRequest(Long reliefId, String reliefNo, Long unionMemberId,
                                    ReliefType reliefType, Integer applyYear, BigDecimal amount) {
}
