package com.bluewhale.lms.course.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 课件保存命令。
 *
 * @author BlueWhale
 */
@Data
public class WareSaveCommand {

    /** 课件名称 */
    @NotBlank(message = "课件名称不能为空")
    @Size(max = 128, message = "课件名称最长 128 字符")
    private String name;

    /** 类型：DOCUMENT / VIDEO / LINK */
    @NotBlank(message = "课件类型不能为空")
    private String wareType;

    /** whale-file 文件引用 ID（DOCUMENT / VIDEO 必填） */
    @Size(max = 64, message = "文件引用 ID 最长 64 字符")
    private String fileRefId;

    /** 外部链接（LINK 必填） */
    @Size(max = 512, message = "外链最长 512 字符")
    private String linkUrl;

    /** 排序号（为空时追加到末尾） */
    private Integer sortNo;
}
