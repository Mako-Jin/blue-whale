package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的关注条目。
 *
 * <p>只回 {@code targetType/targetId}：版块名由前端用已加载的版块树本地映射，
 * 作者昵称平台无跨服务查询，前端按「鲸友 #id」展示。</p>
 *
 * @author BlueWhale
 */
@Data
public class FollowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关注对象类型：USER / BOARD */
    private String targetType;

    /** 关注对象ID */
    private Long targetId;

    /** 关注时间 */
    private LocalDateTime createTime;
}
