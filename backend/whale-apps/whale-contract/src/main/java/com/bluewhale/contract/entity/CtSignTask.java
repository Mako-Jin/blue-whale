package com.bluewhale.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 签署任务实体，对应表 {@code ct_sign_task}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_sign_task")
public class CtSignTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 合同ID */
    private Long contractId;

    /** 合同版本ID */
    private Long versionId;

    /** 签署顺序号 */
    private Integer seqNo;

    /** 签署人用户ID（外部相对方为空） */
    private Long signerUserId;

    /** 签署方：A/B/OTHER */
    private String signerParty;

    /** 签署人姓名 */
    private String signerName;

    /** 签署渠道：LOCAL/ESIGNBAO/FADADA/SSQ */
    private String provider;

    /** 三方平台任务ID（M3 启用） */
    private String providerTaskId;

    /** 状态：PENDING/SIGNING/SIGNED/REFUSED/EXPIRED */
    private String status;

    /** 签署完成时间 */
    private LocalDateTime signedAt;

    /** 签章件文件ID */
    private Long signedFileId;

    /** 拒签原因 */
    private String refuseReason;
}
