package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 最近访问视图。
 *
 * @author BlueWhale
 */
@Data
public class DocRecentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 标题 */
    private String title;

    /** 空间ID */
    private Long spaceId;

    /** 空间名称 */
    private String spaceName;

    /** 页面路径 */
    private String slug;

    /** 最近访问时间 */
    private LocalDateTime visitedAt;

    /** 页面更新时间 */
    private LocalDateTime updateTime;
}
