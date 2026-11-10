package com.bluewhale.doc.infrastructure.persistence.query;

import lombok.Data;

/**
 * 无主页面附件引用查询行（M5 上线硬化）。
 *
 * <p>页面行已被物理删除（回收站彻底清除或历史数据修复）后，附件引用成为孤儿，
 * 由定时任务按租户分批清理；不删除 whale-file 中的文件本体。</p>
 *
 * @author BlueWhale
 */
@Data
public class DocOrphanAttachmentRow {

    /** 租户ID（跨租户清理时使用） */
    private Long tenantId;

    /** 附件引用行ID */
    private Long attachmentId;

    /** 已失效的页面ID */
    private Long pageId;
}
