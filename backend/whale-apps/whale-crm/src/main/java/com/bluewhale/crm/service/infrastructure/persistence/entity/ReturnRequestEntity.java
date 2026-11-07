package com.bluewhale.crm.service.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 退换货申请实体，对应表 {@code crm_return_request}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_return_request")
public class ReturnRequestEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID（后台跨租户扫描时显式携带） */
    private Long tenantId;

    /** 申请单号 */
    private String returnNo;

    /** 申请类型：RETURN / EXCHANGE */
    private String requestType;

    /** 商务订单 ID */
    private Long orderId;

    /** ERP 销售订单号 */
    private String erpOrderNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户编码 */
    private String customerCode;

    /** 客户名称 */
    private String customerName;

    /** 退货原因 */
    private String reason;

    /** 申请状态 */
    private String status;

    /** 审批实例 ID */
    private Long approvalInstanceId;

    /** ERP 退货单号 */
    private String erpReturnNo;

    /** ERP 退货单状态 */
    private String erpStatus;

    /** 下发尝试次数 */
    private Integer pushAttempts;

    /** 最近失败原因 */
    private String lastError;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本号 */
    private Integer lockVersion;
}
