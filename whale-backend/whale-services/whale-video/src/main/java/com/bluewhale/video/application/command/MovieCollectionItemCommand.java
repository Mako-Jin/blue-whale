package com.bluewhale.video.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 电影片单加片命令。
 *
 * @author BlueWhale
 */
@Data
public class MovieCollectionItemCommand {

    /** 电影内容 ID */
    @NotNull(message = "{validation.video.movie-collection-item.content-required}")
    private Long contentId;

    /** 片单内排序（升序），为空表示追加到最后 */
    private Integer sortNo;
}
