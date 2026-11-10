package com.bluewhale.music.application.service;

import com.bluewhale.music.application.command.AgreementAcceptCommand;
import com.bluewhale.music.application.view.AgreementView;

/**
 * 版权无争议协议应用服务。
 *
 * <p>协议版本以服务端配置 {@code whale.music.agreement-version} 为准，
 * 签署记录按「用户 + 版本」唯一保留，用于版权追溯。</p>
 *
 * @author BlueWhale
 */
public interface CopyrightAgreementAppService {

    /**
     * 查询当前用户对当前协议版本的签署状态。
     *
     * @param userId 用户 ID
     * @return 协议状态视图
     */
    AgreementView current(Long userId);

    /**
     * 签署当前协议版本（重复签署幂等，返回已有记录）。
     *
     * @param command 签署命令
     * @param userId 用户 ID
     * @param clientIp 客户端 IP
     * @param userAgent 客户端标识
     * @return 协议状态视图
     */
    AgreementView accept(AgreementAcceptCommand command, Long userId, String clientIp, String userAgent);

    /**
     * 校验用户已签署当前协议版本，未签署直接拒绝。
     *
     * @param userId 用户 ID
     */
    void assertAccepted(Long userId);
}
