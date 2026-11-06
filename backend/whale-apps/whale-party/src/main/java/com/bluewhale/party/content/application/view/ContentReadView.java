package com.bluewhale.party.content.application.view;

import com.bluewhale.party.content.domain.model.ContentRead;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 党建内容阅读记录视图。
 *
 * @author BlueWhale
 */
@Data
public class ContentReadView {

    /** 阅读记录 ID */
    private Long id;

    /** 内容 ID */
    private Long contentId;

    /** 阅读人用户 ID */
    private Long userId;

    /** 阅读人姓名 */
    private String userName;

    /** 最近阅读时间 */
    private LocalDateTime readAt;

    /**
     * 由领域模型构建视图。
     *
     * @param read 阅读记录
     * @return 视图
     */
    public static ContentReadView from(ContentRead read) {
        ContentReadView view = new ContentReadView();
        view.setId(read.getId());
        view.setContentId(read.getContentId());
        view.setUserId(read.getUserId());
        view.setUserName(read.getUserName());
        view.setReadAt(read.getReadAt());
        return view;
    }
}
