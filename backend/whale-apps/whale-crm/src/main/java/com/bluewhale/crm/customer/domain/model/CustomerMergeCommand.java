package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

/**
 * 客户合并命令（M1-4）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerMergeCommand {

    /** 源客户 ID（合并后置为 MERGED 只读） */
    private Long sourceId;

    /** 目标客户 ID（保留并承接引用） */
    private Long targetId;

    /** 是否确认主联系人冲突（目标已有主联系人时，源主联系人默认降级为非主） */
    private Boolean confirmPrimaryContactConflict;

    /** 操作备注（写入负责人留痕） */
    private String operatorNote;
}
