package com.bluewhale.pm.application.notify;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 站内通知事件：由写用例在事务内发布，提交后异步投递。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class PmNotifyEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户ID（发布时从请求上下文捕获） */
    private Long tenantId;

    /** 项目ID */
    private Long projectId;

    /** 工作项ID */
    private Long workItemId;

    /** 工作项键 */
    private String itemKey;

    /** 工作项标题 */
    private String itemTitle;

    /** 通知类型 */
    private PmNotifyKind kind;

    /** 操作人用户ID（不接收自己的通知） */
    private Long operatorId;

    /** 接收人用户ID列表 */
    private List<Long> recipientIds;

    /** 通知明细（评论摘要、状态变化等） */
    private String detail;

    /**
     * 过滤空值后构造接收人列表。
     *
     * @param userIds 候选接收人
     * @return 非空接收人列表
     */
    public static List<Long> recipients(Long... userIds) {
        List<Long> recipients = new ArrayList<>();
        if (userIds != null) {
            for (Long userId : userIds) {
                if (userId != null) {
                    recipients.add(userId);
                }
            }
        }
        return recipients;
    }
}
