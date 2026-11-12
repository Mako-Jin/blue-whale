package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 作者帖子统计（个人主页用，单次聚合查询）。
 *
 * @author BlueWhale
 */
@Data
public class AuthorStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发帖数 */
    private Long threadCount;

    /** 获花总数 */
    private Long likeCount;

    /** 精华数 */
    private Long essenceCount;
}
