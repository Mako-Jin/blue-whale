package com.bluewhale.oa.culture.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 建议箱聚合。
 *
 * <p>匿名建议库内仍保留提交人用于审计，仅在视图层隐藏。</p>
 *
 * @author BlueWhale
 */
@Data
public class Suggestion {

    /** 主键ID */
    private Long id;

    /** 建议标题 */
    private String title;

    /** 类别 */
    private String category;

    /** 建议内容 */
    private String content;

    /** 是否匿名：0 实名 / 1 匿名 */
    private Integer anonymous;

    /** 提交人用户ID */
    private Long submitterId;

    /** 提交人姓名快照 */
    private String submitterName;

    /** 提交人部门ID */
    private Long deptId;

    /** 状态 */
    private String status;

    /** 处理人用户ID */
    private Long handlerId;

    /** 处理人姓名快照 */
    private String handlerName;

    /** 最近处理时间 */
    private LocalDateTime handledTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 是否匿名建议。
     *
     * @return 匿名返回 true
     */
    public boolean isAnonymous() {
        return anonymous != null && anonymous == 1;
    }

    /**
     * 记录回复处理：状态置已回复并登记处理人。
     *
     * @param handlerId 处理人用户ID
     * @param handler 处理人姓名
     * @param time 处理时间
     */
    public void reply(Long handlerId, String handler, LocalDateTime time) {
        if (SuggestionStatus.of(status) == SuggestionStatus.CLOSED) {
            throw OaErrorCode.exception(OaErrorCode.SUGGESTION_STATUS_CONFLICT, "已关闭的建议不可回复");
        }
        this.status = SuggestionStatus.REPLIED.name();
        this.handlerId = handlerId;
        this.handlerName = handler;
        this.handledTime = time;
    }

    /**
     * 关闭建议（幂等）。
     */
    public void close() {
        if (SuggestionStatus.of(status) == SuggestionStatus.CLOSED) {
            return;
        }
        this.status = SuggestionStatus.CLOSED.name();
    }
}
