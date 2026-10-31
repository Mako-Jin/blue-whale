package com.bluewhale.music.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版权无争议协议状态视图。
 *
 * @author BlueWhale
 */
@Data
public class AgreementView {

    /** 当前协议版本号 */
    private String agreementVersion;

    /** 协议标题 */
    private String agreementTitle;

    /** 当前用户是否已签署该版本 */
    private boolean accepted;

    /** 最近签署时间；未签署时为 {@code null} */
    private LocalDateTime acceptedAt;
}
