package com.bluewhale.oa.integration.application.service;

import com.bluewhale.oa.integration.application.command.OutboxAppendCommand;
import com.bluewhale.oa.integration.application.view.OutboxEventView;

import java.util.List;

/**
 * 集成事件 Outbox 应用服务。
 *
 * @author BlueWhale
 */
public interface OutboxAppService {

    /**
     * 追加事件（幂等：同一事件键只落库一次）。
     *
     * @param command 追加命令
     * @return 新增返回 {@code true}，已存在返回 {@code false}
     */
    boolean append(OutboxAppendCommand command);

    /**
     * 查询待发送或到期待重试的事件。
     *
     * @return 事件列表
     */
    List<OutboxEventView> listRetryable();

    /**
     * 标记发送成功。
     *
     * @param id 事件ID
     */
    void markSent(Long id);

    /**
     * 标记发送失败。
     *
     * @param id 事件ID
     * @param error 失败原因
     */
    void markFailed(Long id, String error);
}
