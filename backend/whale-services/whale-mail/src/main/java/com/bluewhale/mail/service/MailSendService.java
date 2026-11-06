package com.bluewhale.mail.service;

import com.bluewhale.mail.dto.MailSendRequest;

/**
 * 发信服务。
 *
 * @author BlueWhale
 */
public interface MailSendService {

    /**
     * 发送邮件，并写入已发送文件夹索引
     *
     * @param request 发信请求
     * @param userId 当前用户ID
     */
    void send(MailSendRequest request, Long userId);
}
