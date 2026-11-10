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
    @NotBlank(message = "章节标题不能为空")
    @Size(max = 128, message = "章节标题最长 128 字符")
    private String title;

    /** 排序号（为空时追加到末尾） */
    private Integer sortNo;

    /** 简介备注 */
    @Size(max = 500, message = "备注最长 500 字符")
    private String remark;
}
