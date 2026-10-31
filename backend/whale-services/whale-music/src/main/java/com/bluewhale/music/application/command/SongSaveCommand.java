package com.bluewhale.music.application.command;

import com.bluewhale.music.domain.model.SongVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 歌曲保存命令（运营与用户上传共用）。
 *
 * @author BlueWhale
 */
@Data
public class SongSaveCommand {

    /** 专辑 ID，{@code null} 表示单曲 */
    private Long albumId;

    /** 歌手 ID */
    @NotNull(message = "歌曲必须关联歌手")
    private Long artistId;

    /** 歌曲标题 */
    @NotBlank(message = "歌曲标题不能为空")
    @Size(max = 120, message = "歌曲标题最多 120 个字符")
    private String title;

    /** 副标题 */
    @Size(max = 200, message = "副标题最多 200 个字符")
    private String subtitle;

    /** 音频文件 ID */
    @NotNull(message = "请先上传音频文件")
    private Long audioFileId;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 歌词文件 ID */
    private Long lyricFileId;

    /** 歌词纯文本 */
    private String lyricText;

    /** 语言：ZH / EN / JA / OTHER */
    @Pattern(regexp = "ZH|EN|JA|OTHER", message = "语言取值非法")
    private String language;

    /** 可见性：PUBLIC 公开 / PRIVATE 仅自听；缺省 PUBLIC */
    private SongVisibility visibility;

    /** 作品来源：ORIGINAL / AUTHORIZED / ROYALTY_FREE；用户上传必填（M5-3） */
    private String source;

    /** 排序权重 */
    private Integer sortWeight;

    /** 标签 ID 集合 */
    private List<Long> tagIds;
}
