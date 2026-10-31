package com.bluewhale.music.application.command;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 歌单重排命令：{@code songIds} 必须包含歌单内全部歌曲且顺序即新排序。
 *
 * @author BlueWhale
 */
@Data
public class ReorderPlaylistCommand {

    /** 有序歌曲 ID 列表 */
    @NotEmpty(message = "重排歌曲 ID 列表不能为空")
    private List<Long> songIds;
}
