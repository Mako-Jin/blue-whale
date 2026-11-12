package com.bluewhale.forum.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.AuditItemVO;

/**
 * 审核台服务（先审后发）。
 *
 * <p>状态机单向：{@code PENDING → PUBLISHED}（通过）或 {@code PENDING → REJECTED}（驳回）。
 * 驳回<b>必须</b>填意见（30020），否则用户不知道改什么，只会反复重投。</p>
 *
 * @author BlueWhale
 */
public interface AuditService {

    /**
     * 待审队列分页。
     *
     * @param targetType THREAD / POST
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作者
     * @return 分页结果
     */
    PageResult<AuditItemVO> page(String targetType, Long current, Long size, ForumActor actor);

    /**
     * 审核通过。
     *
     * @param targetType THREAD / POST
     * @param targetId 对象ID
     * @param remark 审核意见（可空）
     * @param actor 操作者
     */
    void pass(String targetType, Long targetId, String remark, ForumActor actor);

    /**
     * 审核驳回（意见必填）。
     *
     * @param targetType THREAD / POST
     * @param targetId 对象ID
     * @param remark 驳回意见（必填）
     * @param actor 操作者
     */
    void reject(String targetType, Long targetId, String remark, ForumActor actor);
}
