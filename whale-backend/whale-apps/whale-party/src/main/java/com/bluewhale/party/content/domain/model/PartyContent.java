package com.bluewhale.party.content.domain.model;

import com.bluewhale.party.common.PartyErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 党建内容（先审后发）。
 *
 * <p>封面与附件只存 {@code fileId} 引用，字节归 {@code whale-file}；
 * 正文随内容存库，因为党建内容要参与审核与检索，放对象存储会让审核页多一次跨服务下载。</p>
 *
 * @author BlueWhale
 */
@Data
public class PartyContent {

    /** 分类最大长度 */
    public static final int CATEGORY_MAX_LENGTH = 64;

    /** 标题最大长度 */
    public static final int TITLE_MAX_LENGTH = 200;

    /** 摘要最大长度 */
    public static final int SUMMARY_MAX_LENGTH = 500;

    /** 备注最大长度 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 正文最大长度（MEDIUMTEXT 足够，但超长正文应转附件） */
    public static final int BODY_MAX_LENGTH = 65000;

    /** 附件数量上限 */
    public static final int ATTACHMENT_MAX_COUNT = 10;

    /** 指定支部数量上限 */
    public static final int BRANCH_SCOPE_MAX_COUNT = 20;

    /** 主键 */
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
    private List<Long> attachmentFileIds = new ArrayList<>();

    /** 可见范围 */
    private ContentVisibility visibility;

    /** 指定支部 ID 列表（仅 BRANCHES 有效） */
    private List<Long> branchIds = new ArrayList<>();

    /** 状态 */
    private ContentStatus status;

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

    /** 阅读人数（按用户去重） */
    private Integer readCount;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验字段边界与可见范围。
     *
     * <p>正文在草稿阶段允许为空（先建框架再补内容），提交审核时才要求必填。
     * 支部是否存在由应用服务校验（领域层不查库）。</p>
     */
    public void validate() {
        normalizeText();
        normalizeAttachments();
        normalizeVisibility();
        normalizeDefaults();
    }

    /**
     * 归一文本字段并校验长度。
     */
    private void normalizeText() {
        category = requireText(category, CATEGORY_MAX_LENGTH, "内容分类");
        title = requireText(title, TITLE_MAX_LENGTH, "标题");
        summary = limitOptional(summary, SUMMARY_MAX_LENGTH, "摘要");
        remark = limitOptional(remark, REMARK_MAX_LENGTH, "备注");
        body = limitOptional(body, BODY_MAX_LENGTH, "正文");
        if (coverFileId != null && coverFileId <= 0) {
            coverFileId = null;
        }
    }

    /**
     * 必填文本：去空白并校验非空与长度。
     *
     * @param value 原文
     * @param maxLength 最大长度
     * @param fieldName 字段名
     * @return 清洗后的文本
     */
    private String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_INVALID, fieldName + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_INVALID,
                    fieldName + "不得超过 " + maxLength + " 字符");
        }
        return trimmed;
    }

    /**
     * 可选文本：去空白，超长拒绝，空串转 null。
     *
     * @param value 原文
     * @param maxLength 最大长度
     * @param fieldName 字段名
     * @return 清洗后的文本
     */
    private String limitOptional(String value, int maxLength, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > maxLength) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_INVALID,
                    fieldName + "不得超过 " + maxLength + " 字符");
        }
        return trimmed;
    }

    /**
     * 补默认值与作者校验。
     */
    private void normalizeDefaults() {
        if (authorId == null || authorId <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_INVALID, "作者不能为空");
        }
        if (authorName == null || authorName.isBlank()) {
            authorName = "未署名";
        }
        if (status == null) {
            status = ContentStatus.DRAFT;
        }
        if (readCount == null || readCount < 0) {
            readCount = 0;
        }
    }

    /**
     * 清洗附件 ID：去空、去重、保序。
     */
    private void normalizeAttachments() {
        if (attachmentFileIds == null) {
            attachmentFileIds = new ArrayList<>();
            return;
        }
        LinkedHashSet<Long> cleaned = new LinkedHashSet<>();
        for (Long fileId : attachmentFileIds) {
            if (fileId != null && fileId > 0) {
                cleaned.add(fileId);
            }
        }
        if (cleaned.size() > ATTACHMENT_MAX_COUNT) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_INVALID,
                    "附件不得超过 " + ATTACHMENT_MAX_COUNT + " 个");
        }
        attachmentFileIds = new ArrayList<>(cleaned);
    }

    /**
     * 校验并清洗可见范围。
     */
    private void normalizeVisibility() {
        if (visibility == null) {
            visibility = ContentVisibility.ALL_MEMBERS;
        }
        if (branchIds == null) {
            branchIds = new ArrayList<>();
        }
        if (visibility != ContentVisibility.BRANCHES) {
            branchIds = new ArrayList<>();
            return;
        }
        LinkedHashSet<Long> cleaned = new LinkedHashSet<>();
        for (Long branchId : branchIds) {
            if (branchId != null && branchId > 0) {
                cleaned.add(branchId);
            }
        }
        if (cleaned.isEmpty()) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_VISIBILITY_INVALID,
                    "可见范围为「指定支部」时必须至少选择一个支部");
        }
        if (cleaned.size() > BRANCH_SCOPE_MAX_COUNT) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_VISIBILITY_INVALID,
                    "指定支部不得超过 " + BRANCH_SCOPE_MAX_COUNT + " 个");
        }
        branchIds = new ArrayList<>(cleaned);
    }

    /**
     * 去空白并转 null。
     *
     * @param value 原文
     * @return 清洗后的文本
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 当前状态（空值按草稿处理）。
     *
     * @return 状态
     */
    public ContentStatus currentStatus() {
        return status == null ? ContentStatus.DRAFT : status;
    }

    /**
     * 是否为指定作者。
     *
     * @param userId 用户 ID
     * @return 本人返回 true
     */
    public boolean isAuthor(Long userId) {
        return userId != null && userId.equals(authorId);
    }

    /**
     * 提交审核（草稿 → 待审）。
     */
    public void submit() {
        if (currentStatus() != ContentStatus.DRAFT) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_STATE_CONFLICT,
                    "只有草稿可以提交审核");
        }
        if (body == null || body.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_INVALID, "提交审核前请填写正文");
        }
        this.status = ContentStatus.PENDING;
        this.submitTime = LocalDateTime.now();
    }

    /**
     * 审核通过（待审 → 已发布）。
     */
    public void approve() {
        if (currentStatus() != ContentStatus.PENDING) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_STATE_CONFLICT,
                    "只有待审内容可以审核通过");
        }
        this.status = ContentStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    /**
     * 审核退回（待审 → 草稿），意见必填。
     *
     * @param opinion 审核意见
     */
    public void reject(String opinion) {
        if (currentStatus() != ContentStatus.PENDING) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_STATE_CONFLICT,
                    "只有待审内容可以退回");
        }
        if (opinion == null || opinion.isBlank()) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_AUDIT_OPINION_REQUIRED);
        }
        this.status = ContentStatus.DRAFT;
        this.submitTime = null;
    }

    /**
     * 下线（已发布 → 已下线，终态；保留阅读数）。
     */
    public void offline() {
        if (currentStatus() != ContentStatus.PUBLISHED) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_STATE_CONFLICT,
                    "只有已发布内容可以下线");
        }
        this.status = ContentStatus.OFFLINE;
        this.offlineAt = LocalDateTime.now();
    }

    /**
     * 编辑前状态校验。
     *
     * <p>草稿可直接改；已发布内容改动后<b>自动退回待审</b>，不允许编辑即生效；
     * 待审与已下线一律不可编辑。</p>
     *
     * @return 是否由「已发布」退回待审
     */
    public boolean prepareEdit() {
        ContentStatus current = currentStatus();
        if (current == ContentStatus.PENDING) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_STATE_CONFLICT,
                    "待审内容不可编辑，请等待审核结果");
        }
        if (current.isTerminal()) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_STATE_CONFLICT,
                    "已下线内容不可编辑");
        }
        if (current == ContentStatus.PUBLISHED) {
            this.status = ContentStatus.PENDING;
            this.submitTime = LocalDateTime.now();
            return true;
        }
        return false;
    }

    /**
     * 读者是否可见（仅已发布内容参与判定）。
     *
     * @param scope 阅读范围
     * @return 可见返回 true
     */
    public boolean isVisibleTo(ContentScope scope) {
        if (!currentStatus().isPublished() || scope == null) {
            return false;
        }
        if (scope.isManager()) {
            return true;
        }
        ContentVisibility target = visibility == null ? ContentVisibility.ALL_MEMBERS : visibility;
        return switch (target) {
            case ALL_STAFF -> true;
            case ALL_MEMBERS -> scope.isAllMembers();
            case BRANCHES -> scope.coversBranches(branchIds);
        };
    }

    /**
     * 阅读数加一。
     */
    public void markRead() {
        if (readCount == null || readCount < 0) {
            readCount = 0;
        }
        readCount = readCount + 1;
    }
}
