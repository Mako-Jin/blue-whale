package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的收藏项。
 *
 * @author BlueWhale
 */
@Data
public class DocStarItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 空间ID */
    private Long spaceId;

    /** 空间名称 */
    private String spaceName;

    /** 标题 */
    private String title;

    /** 页面路径 */
    private String slug;

    /** 状态 */
    private String status;

    /** 收藏时间 */
    private LocalDateTime starredAt;

    /** 页面更新时间 */
    private LocalDateTime updateTime;
}
