package com.bluewhale.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 系统公告发布请求。
 *
 * @author BlueWhale
 */
@Data
public class AnnouncementPublishRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 公告标题 */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 128, message = "公告标题不能超过 128 字")
    private String title;

    /** 公告正文 */
    @NotBlank(message = "公告正文不能为空")
    private String content;

    /** 业务类型（可选，缺省为 SYSTEM_ANNOUNCE） */
    @Size(max = 32, message = "业务类型不能超过 32 字")
    private String bizType;
}
