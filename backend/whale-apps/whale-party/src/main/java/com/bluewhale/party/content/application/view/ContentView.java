package com.bluewhale.party.content.application.view;

import com.bluewhale.party.content.domain.model.ContentStatus;
import com.bluewhale.party.content.domain.model.ContentVisibility;
import com.bluewhale.party.content.domain.model.PartyContent;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 党建内容视图。
 *
 * @author BlueWhale
 */
@Data
public class ContentView {

    /** 内容 ID */
    private Long id;

    /** 分类 */
    private String category;

    /** 标题 */
    private String title;

    /** 摘要 */
    private String summary;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 正文 */
    private String body;

    /** 附件文件 ID 列表 */
    private List<Long> attachmentFileIds;

    /** 可见范围码 */
    private String visibility;

    /** 可见范围标签 */
    private String visibilityLabel;

    /** 指定支部 ID 列表 */
    private List<Long> branchIds;

    /** 指定支部名称（详情接口回填） */
    private List<String> branchNames;

    /** 状态码 */
    private String status;

    /** 状态标签 */
    private String statusLabel;

    /** 作者用户 ID */
    private Long authorId;

    /** 作者姓名 */
    private String authorName;

    /** 最近提交审核时间 */
    private LocalDateTime submitTime;

    /** 最近发布时间 */
    private LocalDateTime publishedAt;

    /** 下线时间 */
    private LocalDateTime offlineAt;

    /** 阅读人数 */
    private Integer readCount;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由领域模型构建视图。
     *
     * @param content 党建内容
     * @return 视图
     */
    public static ContentView from(PartyContent content) {
        ContentView view = new ContentView();
        view.setId(content.getId());
        view.setCategory(content.getCategory());
        view.setTitle(content.getTitle());
        view.setSummary(content.getSummary());
        view.setCoverFileId(content.getCoverFileId());
        view.setBody(content.getBody());
        view.setAttachmentFileIds(content.getAttachmentFileIds());
        view.setBranchIds(content.getBranchIds());
        ContentVisibility visibility = content.getVisibility() == null
                ? ContentVisibility.ALL_MEMBERS : content.getVisibility();
        view.setVisibility(visibility.name());
        view.setVisibilityLabel(visibility.getLabel());
        ContentStatus status = content.getStatus() == null ? ContentStatus.DRAFT : content.getStatus();
        view.setStatus(status.name());
        view.setStatusLabel(status.getLabel());
        view.setAuthorId(content.getAuthorId());
        view.setAuthorName(content.getAuthorName());
        view.setSubmitTime(content.getSubmitTime());
        view.setPublishedAt(content.getPublishedAt());
        view.setOfflineAt(content.getOfflineAt());
        view.setReadCount(content.getReadCount() == null ? 0 : content.getReadCount());
        view.setRemark(content.getRemark());
        view.setCreateTime(content.getCreateTime());
        view.setUpdateTime(content.getUpdateTime());
        return view;
    }
}
