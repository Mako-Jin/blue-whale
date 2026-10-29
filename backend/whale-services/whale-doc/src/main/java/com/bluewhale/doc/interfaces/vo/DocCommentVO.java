package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 页面评论视图（含一层回复）。
 *
 * @author BlueWhale
 */
@Data
public class DocCommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评论ID */
    private Long id;

    /** 页面ID */
    private Long pageId;

    /** 父评论ID */
    private Long parentId;

    /** 行内锚点块ID */
    private String anchorBlockId;

    /** 锚点摘要 */
    private String anchorText;

    /** 内容 */
    private String content;

    /** 评论人ID */
    private Long authorId;

    /** 是否已解决 */
    private boolean resolved;

    /** 解决人ID */
    private Long resolvedBy;

    /** 解决时间 */
    private LocalDateTime resolvedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 回复 */
    private List<DocCommentVO> replies = new ArrayList<>();
}
