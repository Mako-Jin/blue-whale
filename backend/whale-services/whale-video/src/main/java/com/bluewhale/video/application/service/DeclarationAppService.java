package com.bluewhale.video.application.service;

import com.bluewhale.video.application.command.DeclarationAcceptCommand;
import com.bluewhale.video.application.view.DeclarationView;

/**
 * 上传声明应用服务：查询当前声明与签署留痕。
 *
 * @author BlueWhale
 */
public interface DeclarationAppService {

    /**
     * 读取当前声明与服务端当前版本，并回填当前用户签署状态。
     *
     * @param userId 当前用户 ID
     * @return 声明视图
     */
    DeclarationView current(Long userId);

    /**
     * 签署当前声明（同一用户同一版本幂等）。
     *
     * @param command 签署命令
     * @param userId 当前用户 ID
     * @param clientIp 客户端 IP
     * @param userAgent 客户端标识
     * @return 签署后的声明视图
     */
    DeclarationView accept(DeclarationAcceptCommand command, Long userId, String clientIp, String userAgent);
}
