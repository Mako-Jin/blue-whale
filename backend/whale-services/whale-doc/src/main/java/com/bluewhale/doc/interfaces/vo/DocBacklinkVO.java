package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 反向链接视图。
 *
 * @author BlueWhale
 */
@Data
public class DocBacklinkVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 引用方页面ID */
    private Long pageId;

    /** 引用方标题 */
    private String title;

    /** 空间ID */
    private Long spaceId;

    /** 空间名称 */
    private String spaceName;

    /** 页面路径 */
    private String slug;

    /** 链接文本 */
    private String linkText;

    /** 引用方更新时间 */
    private LocalDateTime updateTime;
}
