package com.bluewhale.doc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档模板实体，对应表 {@code doc_template}。
 *
 * <p>{@code spaceId=0} 表示租户级模板；模板正文与页面一致，使用 TipTap JSON 存储。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_template")
public class DocTemplate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 归属空间ID，0 表示租户级模板 */
    private Long spaceId;

    /** 模板名称 */
    private String name;

    /** 模板说明 */
    private String description;

    /** 节点类型：PAGE / FOLDER */
    private String pageType;

    /** 图标标识 */
    private String icon;

    /** 模板正文 JSON */
    private String contentJson;

    /** 服务端提取的纯文本投影 */
    private String contentText;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 排序 */
    private Integer sortNo;

    /** 创建人ID */
    private Long authorId;
}
