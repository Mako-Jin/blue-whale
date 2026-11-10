package com.bluewhale.video.application.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 内容保存命令（用户上传视频 / 运营入库电影共用）。
 *
 * <p>视频字节先经 {@code whale-file} 分片上传得到 {@code videoFileId}，
 * 本命令只提交元数据；{@code source} 为内容来源声明（版权合规必填）。</p>
 *
 * @author BlueWhale
 */
@Data
public class ContentSaveCommand {

    /** 内容类型：SHORT_VIDEO / VIDEO / MOVIE（为空时由接口路径决定） */
    private String contentType;

    /** 标题 */
    @Size(max = 200, message = "标题不能超过 200 个字符")
    private String title;

    /** 副标题 */
    @Size(max = 200, message = "副标题不能超过 200 个字符")
    private String subtitle;

    /** 简介 */
    @Size(max = 2000, message = "简介不能超过 2000 个字符")
    private String description;

    /** 视频文件 ID */
    @NotNull(message = "视频文件不能为空")
    private Long videoFileId;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 视频宽度 */
    private Integer width;

    /** 视频高度 */
    private Integer height;

    /** 文件字节数 */
    private Long sizeBytes;

    /** 分类 ID */
    private Long categoryId;

    /** 系列 ID，0 表示不属系列（仅视频可入系列） */
    private Long seriesId;

    /** 集数（系列内从 1 开始，非系列内容为 0） */
    private Integer episodeNo;

    /** 创作者 ID */
    private Long creatorId;

    /** 标签 ID 集合 */
    private List<Long> tagIds;

    /** 可见性：PUBLIC / PRIVATE */
    private String visibility;

    /** 内容来源：ORIGINAL / AUTHORIZED / ROYALTY_FREE */
    private String source;

    /** 跳过发布直接上架（用户侧默认 false，运营可置 true） */
    private boolean publishNow;

    /**
     * 兼容前端契约字段 {@code fileId}：等价于 {@code videoFileId}。
     *
     * @param fileId 文件 ID
     */
    public void setFileId(Long fileId) {
        this.videoFileId = fileId;
    }

    /**
     * 兼容前端契约字段 {@code contentSource}：等价于 {@code source}。
     *
     * @param contentSource 内容来源
     */
    public void setContentSource(String contentSource) {
        this.source = contentSource;
    }
}
