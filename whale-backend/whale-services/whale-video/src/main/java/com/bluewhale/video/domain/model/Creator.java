package com.bluewhale.video.domain.model;

import lombok.Data;

/**
 * 视频创作者资料（每用户一条，停用后不得发布新内容）。
 *
 * @author BlueWhale
 */
@Data
public class Creator {

    /** 主键 */
    private Long id;

    /** 关联用户 ID */
    private Long userId;

    /** 创作者昵称 */
    private String displayName;

    /** 头像文件 ID（whale-file，公开视图不暴露） */
    private Long avatarFileId;

    /** 简介 */
    private String bio;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /**
     * 判断创作者是否可发布新内容。
     *
     * @return 启用状态返回 {@code true}
     */
    public boolean active() {
        return status != null && status == 1;
    }
}
