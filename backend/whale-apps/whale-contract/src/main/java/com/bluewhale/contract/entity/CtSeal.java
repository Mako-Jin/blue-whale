package com.bluewhale.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 印章实体，对应表 {@code ct_seal}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_seal")
public class CtSeal extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 印章名称 */
    private String name;

    /** 印章类型：COMPANY/CONTRACT/LEGAL/FINANCE */
    private String sealType;

    /** 印章图片文件ID（whale-file，空则使用内置兜底印章） */
    private Long imageFileId;

    /** 印章保管人用户ID */
    private Long keeperUserId;

    /** 有效期起 */
    private LocalDate validFrom;

    /** 有效期止 */
    private LocalDate validTo;

    /** 状态：ENABLED/DISABLED */
    private String status;
}
