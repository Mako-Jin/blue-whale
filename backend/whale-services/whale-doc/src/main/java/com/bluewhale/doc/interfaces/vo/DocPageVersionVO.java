package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 版本快照视图。
 *
 * @author BlueWhale
 */
@Data
public class DocPageVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long id;

    /** 页面ID */
    private Long pageId;

    /** 快照 revision */
    private Long revisionNo;

    /** 快照标题 */
    private String title;

    /** 快照类型 */
    private String versionKind;

    /** 变更说明 */
    private String changeSummary;

    /** 编辑人ID */
    private Long editorId;

    /** 正文 JSON（仅详情返回） */
    private String contentJson;

    /** 创建时间 */
    private LocalDateTime createTime;
}
