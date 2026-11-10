package com.bluewhale.video.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 电影片单保存命令（运营维护片单标题 / 简介 / 封面 / 排序）。
 *
 * @author BlueWhale
 */
@Data
public class MovieCollectionSaveCommand {

    /** 片单标题 */
    @NotBlank(message = "片单标题不能为空")
    @Size(max = 200, message = "片单标题不能超过 200 个字符")
    private String title;

    /** 片单简介 */
    @Size(max = 2000, message = "片单简介不能超过 2000 个字符")
    private String description;

    /** 封面文件 ID；更新时为空表示保留原封面，0 表示清除 */
    private Long coverFileId;

    /** 运营排序（升序） */
    private Integer sortNo;
}
