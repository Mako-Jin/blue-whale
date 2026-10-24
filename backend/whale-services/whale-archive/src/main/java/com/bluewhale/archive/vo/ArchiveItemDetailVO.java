package com.bluewhale.archive.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 档案条目详情视图。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveItemDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 条目主体信息 */
    private ArchiveItemVO item;

    /** 条目关联文件（原件 / 附件 / 快照） */
    private List<ArchiveFileVO> files;

    /** 来源实例当前状态（工作流不可达时为 {@code null}） */
    private Integer sourceStatus;
}
