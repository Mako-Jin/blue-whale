package com.bluewhale.pm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作项附件实体，对应表 {@code pm_attachment}（仅保存 file_id 引用）。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pm_attachment")
public class PmAttachment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 工作项ID */
    private Long workItemId;

    /** 文件ID */
    private Long fileId;

    /** 文件名 */
    private String fileName;

    /** 上传人用户ID */
    private Long uploaderId;
}
