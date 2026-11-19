package com.bluewhale.music.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 专辑保存命令。
 *
 * @author BlueWhale
 */
@Data
public class AlbumSaveCommand {

    /** 歌手 ID */
    @NotNull(message = "{validation.music.album.artist-required}")
    private Long artistId;

    /** 专辑标题 */
    @NotBlank(message = "{validation.music.album.title-required}")
    @Size(max = 200, message = "{validation.music.album.title-size}")
    private String title;

    /** 封面文件 ID */
    @NotNull(message = "{validation.music.album.cover-required}")
    private Long coverFileId;

    /** 发行日期 */
    private LocalDate releaseDate;

    /** 专辑类型：SINGLE / EP / ALBUM / COMPILATION；缺省 ALBUM */
    @Pattern(regexp = "SINGLE|EP|ALBUM|COMPILATION", message = "{validation.music.album.type-pattern}")
    private String albumType;

    /** 简介 */
    @Size(max = 1000, message = "{validation.music.album.intro-size}")
    private String intro;

    /** 排序 */
    private Integer sortNo;
}
