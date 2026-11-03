package com.bluewhale.video.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 视频服务运行元信息。
 *
 * <p>供前端子应用启动探测与上传能力开关读取；不包含任何内容或用户作品数据，
 * 也不暴露任何文件 ID。</p>
 *
 * @author BlueWhale
 */
@Data
public class VideoMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务名 */
    private String service;

    /** 当前租户ID */
    private Long tenantId;

    /** 当前用户ID（网关可信头原始值） */
    private String userId;

    /** 当前《内容合法与版权无争议声明》版本 */
    private String declarationVersion;

    /** 当前《内容合法与版权无争议声明》标题 */
    private String declarationTitle;

    /** 是否允许登录用户上传短视频 / 视频 */
    private boolean uploadEnabled;

    /** 支持的内容类型：SHORT_VIDEO / VIDEO / MOVIE */
    private List<String> contentTypes;

    /** 短视频单文件上限（字节） */
    private long shortMaxBytes;

    /** 视频单文件上限（字节） */
    private long videoMaxBytes;

    /** 电影单文件上限（字节） */
    private long movieMaxBytes;

    /** 播放票据有效期（秒） */
    private int playTicketTtlSeconds;

    /** 播放票据建议刷新间隔（秒） */
    private int playTicketRefreshSeconds;

    /** 评论是否先审后发 */
    private boolean commentPreAudit;
}
