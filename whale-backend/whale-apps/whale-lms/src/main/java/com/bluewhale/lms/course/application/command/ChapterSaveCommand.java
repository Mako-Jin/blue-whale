package com.bluewhale.lms.course.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 章节保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ChapterSaveCommand {

    /** 章节标题 */
    @NotBlank(message = "{validation.lms.chapter.title-required}")
    @Size(max = 128, message = "{validation.lms.chapter.title-size}")
    private String title;

    /** 排序号（为空时追加到末尾） */
    private Integer sortNo;

    /** 简介备注 */
    @Size(max = 500, message = "{validation.lms.chapter.remark-size}")
    private String remark;
}
