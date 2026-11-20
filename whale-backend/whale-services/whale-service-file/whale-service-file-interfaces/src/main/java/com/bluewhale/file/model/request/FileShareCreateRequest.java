package com.bluewhale.file.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建分享链接请求。
 *
 * @author BlueWhale
 */
@Data
public class FileShareCreateRequest {

    /** 被分享的文件ID */
    @NotNull(message = "{validation.file.common.file-id-required}")
    private Long fileId;

    /** 有效小时数；为空时取配置默认值，超过配置上限时截断 */
    @Min(value = 1, message = "{validation.file.share.expire-hours-min}")
    private Integer expireHours;

    /** 下载次数上限；为空或 0 表示不限 */
    @Min(value = 0, message = "{validation.file.share.max-downloads-min}")
    private Integer maxDownloads;

    /** 分享密码；为空表示无密码（4~64 个字符由应用层校验） */
    @Size(max = 64, message = "{validation.file.share.password-size}")
    private String password;
}
