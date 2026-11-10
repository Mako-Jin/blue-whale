package com.bluewhale.pm.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作项附件视图对象
 *
 * @author BlueWhale
 */
@Data
public class WorkItemAttachmentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private String id;

    /** 工作项ID */
    private String workItemId;

    /** 文件ID */
    private String fileId;

    /** 文件名 */
    private String fileName;

    /** 上传人用户ID */
    private String uploaderId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
