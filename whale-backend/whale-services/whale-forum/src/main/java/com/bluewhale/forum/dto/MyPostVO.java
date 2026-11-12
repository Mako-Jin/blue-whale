package com.bluewhale.forum.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的回复条目（F-07-02）。
 *
 * @author BlueWhale
 */
@Data
public class MyPostVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 楼层ID */
    private Long postId;

    /** 所属帖子ID */
    private Long threadId;

    /** 所属帖子标题 */
    private String threadTitle;

    /** 楼层号（二级回复为 null） */
    private Integer floor;

    /** 回复摘要（纯文本，服务端截断） */
    private String summary;

    /** 回复原文（服务层转摘要后清空，不落到响应里） */
    @JsonIgnore
    private String content;

    /** 投花数 */
    private Integer likeCount;

    /** 楼层状态：PUBLISHED / REMOVED */
    private String status;

    /** 回复时间 */
    private LocalDateTime createTime;
}
