package com.bluewhale.video.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论审核视图：在评论视图基础上补充审核状态。
 *
 * <p>仅运营审核接口返回；用户侧评论视图不暴露状态。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentAuditView extends CommentView {

    private static final long serialVersionUID = 1L;

    /** 审核状态：PENDING / VISIBLE / HIDDEN */
    private String status;
}
