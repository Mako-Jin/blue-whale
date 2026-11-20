package com.bluewhale.video.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 系列保存命令（运营维护视频系列）。
 *
 * @author BlueWhale
 */
@Data
public class SeriesSaveCommand {

    /** 系列标题 */
    @NotBlank(message = "{validation.video.series.title-required}")
    @Size(max = 200, message = "{validation.video.series.title-size}")
    private String title;

    /** 系列简介 */
    @Size(max = 2000, message = "{validation.video.series.description-size}")
    private String description;

    /** 封面文件 ID；更新时为空表示保留原封面，0 表示清除封面 */
    private Long coverFileId;

    /** 分类 ID，0 表示未分类 */
    private Long categoryId;

    /** 创作者 ID，0 表示未登记 */
    private Long creatorId;

    /** 运营排序（升序） */
    private Integer sortNo;
}
