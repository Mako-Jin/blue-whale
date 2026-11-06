package com.bluewhale.mail.service;

import com.bluewhale.mail.dto.MailProvisionRequest;
import com.bluewhale.mail.vo.MailProvisionVO;

/**
 * 平台代开邮箱服务：为平台用户创建真实邮箱并落账号索引。
 *
 * <p>调用方只有 {@code whale-auth}（内网）与运维补开场景，不对终端用户开放。
 * 接口<b>幂等</b>：同一用户重复调用返回 {@code EXISTS}，不会创建第二个邮箱。</p>
 *
 * @author BlueWhale
 */
public interface MailProvisionService {

    /**
     * 为用户开通邮箱
     *
     * @param request 开通请求
     * @return 开通结果（不含初始口令）
     */
    MailProvisionVO provision(MailProvisionRequest request);
}
