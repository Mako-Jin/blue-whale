package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模板视图（M3）。
 *
 * @author BlueWhale
 */
@Data
public class DocTemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板ID */
    private Long id;

    /** 归属空间ID，0 表示租户级 */
    private Long spaceId;

    /** 模板名称 */
    private String name;

    /** 模板说明 */
    private String description;

    /** 节点类型 */
    private String pageType;

    /** 图标 */
    private String icon;

    /** 正文 JSON 字符串 */
    private String contentJson;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 排序 */
    private Integer sortNo;

    /** 创建人ID */
    private Long authorId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
