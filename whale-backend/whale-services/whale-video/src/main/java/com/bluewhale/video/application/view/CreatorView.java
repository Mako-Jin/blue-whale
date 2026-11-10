package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;

/**
 * 创作者视图（头像走代理地址，不暴露文件 ID）。
 *
 * @author BlueWhale
 */
@Data
public class CreatorView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 创作者 ID */
    private Long id;

    /** 关联用户 ID */
    private Long userId;

    /** 创作者昵称 */
    private String displayName;

    /** 简介 */
    private String bio;

    /** 是否启用 */
    private boolean active;

    /** 头像代理地址（无头像时为 {@code null}） */
    private String avatarUrl;
}
