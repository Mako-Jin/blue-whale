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
    @NotBlank(message = "{validation.message.announcement.title-required}")
    @Size(max = 128, message = "{validation.message.announcement.title-size}")
    private String title;

    /** 公告正文 */
    @NotBlank(message = "{validation.message.announcement.content-required}")
    private String content;

    /** 业务类型（可选，缺省为 SYSTEM_ANNOUNCE） */
    @Size(max = 32, message = "{validation.message.announcement.biz-type-size}")
    private String bizType;
}
