package com.bluewhale.music.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 音乐服务运行元信息。
 *
 * <p>供前端子应用启动探测与能力开关读取，不包含任何用户作品数据。</p>
 *
 * @author BlueWhale
 */
@Data
public class MusicMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务名 */
    private String service;

    /** 当前租户ID */
    private Long tenantId;

    /** 当前用户ID（网关可信头原始值） */
    private String userId;

    /** 当前《版权无争议协议》版本 */
    private String agreementVersion;

    /** 当前《版权无争议协议》标题 */
    private String agreementTitle;

    /** 是否允许登录用户上传作品 */
    private boolean uploadEnabled;

    /** 上传音频大小上限（字节） */
    private long uploadMaxBytes;

    /** 允许上传的音频 MIME 类型 */
    private List<String> audioFormats;

    /** 播放票据有效期（秒） */
    private int playTicketTtlSeconds;

    /** 评论是否先审后发 */
    private boolean commentPreAudit;
}
