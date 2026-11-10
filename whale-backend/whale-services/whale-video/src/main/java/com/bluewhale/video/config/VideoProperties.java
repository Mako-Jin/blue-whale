package com.bluewhale.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频服务运行参数（配置前缀 {@code whale.video}）。
 *
 * <p>所有参数均可通过环境变量覆盖；默认值面向本机开发环境，生产按部署侧配置调整。
 * 上传声明版本用于「短视频 / 视频」上传前置校验与签署留痕；电影仅运营入库，
 * 不走用户上传声明。播放票据 TTL 默认 300s，客户端在 240s 主动刷新。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.video")
public class VideoProperties {

    /** 当前《内容合法与版权无争议声明》版本号，上传时必须签署该版本 */
    private String declarationVersion = "V1";

    /** 当前《内容合法与版权无争议声明》标题 */
    private String declarationTitle = "内容合法与版权无争议声明";

    /** 短视频时长上限（秒），默认 5 分钟 */
    private int shortMaxDurationSeconds = 300;

    /** 视频时长上限（秒），默认 2 小时 */
    private int videoMaxDurationSeconds = 7200;

    /** 电影时长上限（秒），默认 4 小时 */
    private int movieMaxDurationSeconds = 14400;

    /** 短视频单文件大小上限（字节），默认 300MB */
    private long shortMaxBytes = 314572800L;

    /** 视频单文件大小上限（字节），默认 2GB */
    private long videoMaxBytes = 2147483648L;

    /** 电影单文件大小上限（字节），默认 4GB */
    private long movieMaxBytes = 4294967296L;

    /** 允许上传的视频 MIME 类型（逗号分隔配置） */
    private List<String> videoFormats = new ArrayList<>(List.of("video/mp4", "video/webm"));

    /** 允许上传的封面 / 海报 MIME 类型 */
    private List<String> imageFormats = new ArrayList<>(List.of("image/jpeg", "image/png", "image/webp"));

    /** 播放票据有效期（秒）：一次播放会发起多次 Range 请求，票据需可复用 */
    private int playTicketTtlSeconds = 300;

    /** 播放票据建议刷新间隔（秒），客户端据此提前重取票据 */
    private int playTicketRefreshSeconds = 240;

    /** 可播状态缓存 TTL（秒）：下架 / 版权下架 / 授权到期主动删除缓存 */
    private int playableCacheSeconds = 30;

    /** 流式响应单块读取字节数 */
    private long streamChunkBytes = 1048576L;

    /** 评论是否先审后发；默认 false 表示先发后审 + 审核队列 */
    private boolean commentPreAudit = false;

    /** 观看历史保留天数 */
    private int historyRetentionDays = 180;

    /** 内容缓存 TTL（秒），首页 / feed / 详情 */
    private int contentCacheSeconds = 60;

    /** feed 首屏缓存 TTL（秒），内容发布 / 下架 / 版权下架时按租户整体失效 */
    private int feedCacheSeconds = 30;

    /** 文件服务（Nacos 服务名） */
    private String fileServiceName = "whale-file";

    /** 视频文件桶（建议独立桶；本地可留空使用默认桶） */
    private String fileBucket = "whale-video";
}
