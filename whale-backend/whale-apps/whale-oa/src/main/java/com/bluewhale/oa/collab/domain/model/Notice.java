package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告聚合根。
 *
 * <p>状态机：{@code DRAFT → PUBLISHED → WITHDRAWN}；草稿可编辑，
 * 发布需接收人快照，撤回仅从已发布状态发起。</p>
 *
 * @author BlueWhale
 */
@Data
public class Notice {

    /** 主键ID */
    private Long id;

    /** 公告标题 */
    private String title;

    /** 公告正文 */
    private String content;

    /** 发布范围 */
    private String scopeType;

    /** 范围值 */
    private String scopeValue;

    /** 状态 */
    private String status;

    /** 发布人账号 */
    private String publisher;

    /** 发布人用户ID */
    private Long publisherId;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 接收人快照 */
    private List<NoticeRecipient> recipients;

    /** 当前用户阅读标记（我的公告列表填充，非持久化字段） */
    private Integer myReadFlag;

    /** 当前用户阅读时间（我的公告列表填充，非持久化字段） */
    private LocalDateTime myReadTime;

    /**
     * 校验是否可编辑。
     */
    public void assertEditable() {
        if (NoticeStatus.of(status) != NoticeStatus.DRAFT) {
            throw OaErrorCode.exception(OaErrorCode.NOTICE_STATUS_CONFLICT, "仅草稿状态的公告可编辑");
        }
    }

    /**
     * 发布公告。
     *
     * @param operator 发布人账号
     * @param operatorId 发布人用户ID
     * @param time 发布时间
     */
    public void publish(String operator, Long operatorId, LocalDateTime time) {
        if (NoticeStatus.of(status) != NoticeStatus.DRAFT) {
            throw OaErrorCode.exception(OaErrorCode.NOTICE_STATUS_CONFLICT, "仅草稿状态的公告可发布");
        }
        this.status = NoticeStatus.PUBLISHED.name();
        this.publisher = operator;
        this.publisherId = operatorId;
        this.publishTime = time;
    }

    /**
     * 撤回公告（已发布 → 已撤回）。
     */
    public void withdraw() {
        if (NoticeStatus.of(status) != NoticeStatus.PUBLISHED) {
            throw OaErrorCode.exception(OaErrorCode.NOTICE_STATUS_CONFLICT, "仅已发布的公告可撤回");
        }
        this.status = NoticeStatus.WITHDRAWN.name();
    }
}
