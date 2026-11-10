package com.bluewhale.oa.approval.domain.gateway;

import com.bluewhale.oa.approval.domain.model.ApprovalNotifyMessage;

/**
 * 审批通知投递端口。
 *
 * <p>具体渠道（站内信/邮件/短信）由消息服务决定；审批上下文只声明模板与接收人。</p>
 *
 * @author BlueWhale
 */
public interface ApprovalNotifyGateway {

    /**
     * 投递通知（消息服务按幂等键去重）。
     *
     * @param message 通知消息
     */
    void send(ApprovalNotifyMessage message);
}
