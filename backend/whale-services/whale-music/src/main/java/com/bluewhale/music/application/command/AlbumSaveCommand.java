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
    @NotNull(message = "专辑必须关联歌手")
    private Long artistId;

    /** 专辑标题 */
    @NotBlank(message = "专辑标题不能为空")
    @Size(max = 200, message = "专辑标题最多 200 个字符")
    private String title;

    /** 封面文件 ID */
    @NotNull(message = "专辑封面不能为空")
    private Long coverFileId;

    /** 发行日期 */
    private LocalDate releaseDate;

    /** 专辑类型：SINGLE / EP / ALBUM / COMPILATION；缺省 ALBUM */
    @Pattern(regexp = "SINGLE|EP|ALBUM|COMPILATION", message = "专辑类型非法")
    private String albumType;

    /** 简介 */
    @Size(max = 1000, message = "专辑简介最多 1000 个字符")
    private String intro;

    /** 排序 */
    private Integer sortNo;
}
