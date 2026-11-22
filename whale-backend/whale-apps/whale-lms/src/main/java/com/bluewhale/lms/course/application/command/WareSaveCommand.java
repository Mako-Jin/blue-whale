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
    @NotBlank(message = "{validation.lms.ware.name-required}")
    @Size(max = 128, message = "{validation.lms.ware.name-size}")
    private String name;

    /** 类型：DOCUMENT / VIDEO / LINK */
    @NotBlank(message = "{validation.lms.ware.ware-type-required}")
    private String wareType;

    /** whale-file 文件引用 ID（DOCUMENT / VIDEO 必填） */
    @Size(max = 64, message = "{validation.lms.ware.file-ref-id-size}")
    private String fileRefId;

    /** 外部链接（LINK 必填） */
    @Size(max = 512, message = "{validation.lms.ware.link-url-size}")
    private String linkUrl;

    /** 排序号（为空时追加到末尾） */
    private Integer sortNo;
}
