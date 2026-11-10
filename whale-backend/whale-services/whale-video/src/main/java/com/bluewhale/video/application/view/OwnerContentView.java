package com.bluewhale.video.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 上传者侧内容视图：在用户侧视图基础上补充发布与版权状态。
 *
 * <p>仅「我的作品」接口返回；详情、feed、搜索等公开接口仍返回
 * {@link PublicContentView}。本视图同样不包含任何文件 ID。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OwnerContentView extends PublicContentView {

    private static final long serialVersionUID = 1L;

    /** 发布状态：DRAFT / PENDING_REVIEW / PUBLISHED / OFFLINE */
    private String publishState;

    /** 版权状态：AUTHORIZED / TAKEDOWN */
    private String copyrightState;

    /** 版权下架原因 */
    private String takedownReason;

    /** 创建时间 */
    private LocalDateTime createTime;
}
