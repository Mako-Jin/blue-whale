package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.command.ChangeOwnPasswordCommand;

/**
 * 自助改密应用服务（REQ-SEC-023）：当前登录用户修改本人口令。
 *
 * <p>校验顺序：旧口令正确性 → 新口令强度 → 历史复用 → 改密并清除强制改密标记 → 记录历史。
 * 改密成功后发布 {@code PASSWORD_CHANGED} 领域事件，由基础设施层写入用户级撤销时间戳，
 * 使该用户全部存量 access/refresh 令牌立即失效（需重新登录）。</p>
 *
 * @author BlueWhale
 */
public interface PasswordAppService {

    /**
     * 修改当前登录用户口令
     *
     * @param command 自助改密命令（旧口令 + 新口令）
     */
    void changeOwnPassword(ChangeOwnPasswordCommand command);
}
