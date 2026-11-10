package com.bluewhale.doc.infrastructure.persistence.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档搜索命中行（含纯文本投影，供服务端生成摘要）。
 *
 * @author BlueWhale
 */
@Data
public class DocSearchRow {

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

    /** revision */
    private Long revision;

    /** 纯文本投影 */
    private String contentText;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
