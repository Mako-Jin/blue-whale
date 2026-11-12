package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签聚合条目（F-06-07 标签页）。
 *
 * @author BlueWhale
 */
@Data
public class TagStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标签名 */
    private String tag;

    /** 该标签下当前用户可见的已发布帖子数 */
    private Long threadCount;
}
