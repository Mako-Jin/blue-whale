package com.bluewhale.oa.culture.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文化动态聚合（大事记 / 活动 / 动态）。
 *
 * <p>状态机：{@code DRAFT → PUBLISHED → ARCHIVED}；仅草稿可编辑删除，
 * 发布仅草稿发起且幂等，发布后不可再编辑，归档不可逆。</p>
 *
 * @author BlueWhale
 */
@Data
public class CultureEvent {

    /** 主键ID */
    private Long id;

    /** 动态类型 */
    private String eventType;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 发生日期 */
    private LocalDate eventDate;

    /** 封面文件ID */
    private Long coverFileId;

    /** 状态 */
    private String status;

    /** 发布人用户ID */
    private Long publisherId;

    /** 发布人姓名快照 */
    private String publisherName;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验是否可编辑/删除（仅草稿可编辑，发布后不可再编辑）。
     */
    public void assertEditable() {
        if (CultureEventStatus.of(status) != CultureEventStatus.DRAFT) {
            throw OaErrorCode.exception(OaErrorCode.CULTURE_EVENT_STATUS_CONFLICT,
                    "仅草稿状态的文化动态可编辑或删除");
        }
    }

    /**
     * 发布文化动态（草稿 → 已发布）。
     *
     * <p>已发布直接返回实现幂等；已归档不可再发布。</p>
     *
     * @param operatorId 发布人用户ID
     * @param operator 发布人姓名
     * @param time 发布时间
     */
    public void publish(Long operatorId, String operator, LocalDateTime time) {
        CultureEventStatus current = CultureEventStatus.of(status);
        if (current == CultureEventStatus.PUBLISHED) {
            return;
        }
        if (current != CultureEventStatus.DRAFT) {
            throw OaErrorCode.exception(OaErrorCode.CULTURE_EVENT_STATUS_CONFLICT,
                    "仅草稿状态的文化动态可发布");
        }
        this.status = CultureEventStatus.PUBLISHED.name();
        this.publisherId = operatorId;
        this.publisherName = operator;
        this.publishTime = time;
    }
}
