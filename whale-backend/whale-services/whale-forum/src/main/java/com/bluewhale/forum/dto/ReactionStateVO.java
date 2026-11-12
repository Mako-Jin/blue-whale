package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前用户对某帖的互动状态（投花/藏帖按钮的选中态与最新计数）。
 *
 * <p>计数由服务端在写后回读，前端不用「本地 +1 再等刷新」——
 * 并发场景下本地加减会和真实值越差越远。</p>
 *
 * @author BlueWhale
 */
@Data
public class ReactionStateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前用户是否已投花 */
    private Boolean liked;

    /** 当前用户是否已藏帖 */
    private Boolean collected;

    /** 投花总数 */
    private Integer likeCount;

    /** 收藏总数 */
    private Integer collectCount;
}
