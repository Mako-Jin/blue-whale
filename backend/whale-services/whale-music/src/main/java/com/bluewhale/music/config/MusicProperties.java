package com.bluewhale.music.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 音乐服务运行参数（配置前缀 {@code whale.music}）。
 *
 * <p>所有参数均可通过环境变量覆盖；默认值面向本机开发环境，
 * 生产按部署侧配置调整。版权协议版本用于上传前置校验与签署留痕。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.music")
public class MusicProperties {

    /** 当前《版权无争议协议》版本号，上传时必须签署该版本 */
    private String agreementVersion = "V1";

    /** 当前《版权无争议协议》标题 */
    private String agreementTitle = "音乐作品版权无争议协议";

    /** 单个音频文件大小上限（字节），默认 100MB */
    private long uploadMaxBytes = 104857600L;

    /** 允许上传的音频 MIME 类型（逗号分隔配置） */
    private List<String> audioFormats = new ArrayList<>(List.of("audio/mpeg", "audio/aac", "audio/flac"));

    /** 允许上传的封面 MIME 类型 */
    private List<String> coverFormats = new ArrayList<>(List.of("image/jpeg", "image/png", "image/webp"));

    /** 播放票据有效期（秒），浏览器一次播放会发起多次 Range 请求，票据需可复用 */
    private int playTicketTtlSeconds = 120;

    /** 流式响应单块读取字节数 */
    private long streamChunkBytes = 1048576L;

    /** 评论是否先审后发；默认 false 表示先发后审 + 审核队列 */
    private boolean commentPreAudit = false;

    /** 是否启用评论回复 / 审核结果站内通知（旁路能力，失败仅告警） */
    private boolean commentNotifyEnabled = true;

    /** 消息服务（Nacos 服务名） */
    private String messageServiceName = "whale-message";

    /** 单个歌单最多歌曲数 */
    private int playlistMaxSongs = 500;

    /** 播放历史保留天数 */
    private int playHistoryRetentionDays = 90;

    /** 文件服务（Nacos 服务名） */
    private String fileServiceName = "whale-file";
}
