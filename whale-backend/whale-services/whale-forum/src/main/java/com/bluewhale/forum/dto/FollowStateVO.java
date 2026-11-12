package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 关注态（详情页 / 版块页按钮选中态）。
 *
 * @author BlueWhale
 */
@Data
public class FollowStateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前用户是否已关注 */
    private Boolean following;
}
