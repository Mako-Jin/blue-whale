package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创作者运营视图（含状态与头像代理地址，不暴露头像文件 ID）。
 *
 * @author BlueWhale
 */
@Data
public class CreatorAdminView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 创作者 ID */
    private Long id;

    /** 关联用户 ID */
    private Long userId;

    /** 创作者昵称 */
    private String displayName;

    /** 简介 */
    private String bio;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 是否可发布新内容 */
    private boolean active;

    /** 头像代理地址（无头像时为 {@code null}） */
    private String avatarUrl;

    /** 创建时间 */
    private LocalDateTime createTime;
}
