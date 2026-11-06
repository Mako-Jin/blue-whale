package com.bluewhale.party.content.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 党建内容阅读记录。
 *
 * <p>同一用户对同一内容只记一条：阅读数按<b>人数</b>统计，
 * 每刷新一次页面就+1 会让「多少党员看过」变成「页面被打开过几次」。</p>
 *
 * @author BlueWhale
 */
@Data
public class ContentRead {

    /** 主键 */
    private Long id;

    /** 内容 ID */
    private Long contentId;

    /** 阅读人用户 ID */
    private Long userId;

    /** 阅读人姓名 */
    private String userName;

    /** 最近阅读时间 */
    private LocalDateTime readAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
