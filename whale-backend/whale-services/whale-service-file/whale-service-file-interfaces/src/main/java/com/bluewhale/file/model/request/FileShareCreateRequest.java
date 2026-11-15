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
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    /** 有效小时数；为空时取配置默认值，超过配置上限时截断 */
    @Min(value = 1, message = "分享有效期必须大于 0 小时")
    private Integer expireHours;

    /** 下载次数上限；为空或 0 表示不限 */
    @Min(value = 0, message = "下载次数上限不能为负数")
    private Integer maxDownloads;

    /** 分享密码；为空表示无密码（4~64 个字符由应用层校验） */
    @Size(max = 64, message = "分享密码不能超过 64 个字符")
    private String password;
}
