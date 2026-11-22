package com.bluewhale.pdm.change.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 变更通知（ECN）保存请求
 *
 * @author BlueWhale
 */
@Data
public class PdmChgNoticeSaveRequest {

    /** 来源变更单ID */
    @NotNull(message = "{validation.pdm.pdm-chg-notice.eco-id-required}")
    private Long ecoId;

    /** 通知标题 */
    @NotBlank(message = "{validation.pdm.pdm-chg-notice.title-required}")
    @Size(max = 200, message = "{validation.pdm.pdm-chg-notice.title-size}")
    private String title;

    /** 通知内容 */
    private String content;

    /** 接收人ID */
    private List<Long> notifyTargets;

    /** 通知渠道 */
    private List<String> notifyChannels;

    /** 生效日期 */
    @NotNull(message = "{validation.pdm.pdm-chg-notice.effective-from-required}")
    private LocalDateTime effectiveFrom;
}
