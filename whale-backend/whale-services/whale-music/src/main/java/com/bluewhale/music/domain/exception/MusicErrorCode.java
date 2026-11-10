package com.bluewhale.music.domain.exception;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * 音乐服务业务错误码（M1 起返回，HTTP 状态由全局异常处理器映射）。
 *
 * <p>段位约定：{@code 400xx} 业务校验失败，{@code 403xx} 数据权限不足，
 * {@code 404xx} 资源不存在，{@code 409xx} 状态冲突。</p>
 *
 * @author BlueWhale
 */
public final class MusicErrorCode {

    /** 歌手名称在租户内重复 */
    public static final int ARTIST_NAME_DUPLICATED = 40001;

    /** 标签名称/类型组合重复 */
    public static final int TAG_DUPLICATED = 40002;

    /** 专辑关联的歌手不存在或已停用 */
    public static final int ALBUM_ARTIST_INVALID = 40003;

    /** 歌曲发布前置校验未通过 */
    public static final int SONG_NOT_READY = 40004;

    /** 版权下架必须填写原因 */
    public static final int TAKEDOWN_REASON_REQUIRED = 40005;

    /** 未签署当前版本的版权无争议协议 */
    public static final int AGREEMENT_REQUIRED = 40006;

    /** 歌曲标题为空或超长 */
    public static final int SONG_TITLE_INVALID = 40007;

    /** 协议版本与服务端当前版本不一致 */
    public static final int AGREEMENT_VERSION_MISMATCH = 40008;

    /** 播放进度参数非法 */
    public static final int PLAY_PROGRESS_INVALID = 40009;

    /** 歌单标题为空或超长 */
    public static final int PLAYLIST_TITLE_INVALID = 40010;

    /** 歌单歌曲数量已达上限 */
    public static final int PLAYLIST_SONG_LIMIT = 40011;

    /** 歌单歌曲 ID 非法或重排顺序与歌单内容不一致 */
    public static final int PLAYLIST_SONG_ORDER_INVALID = 40012;

    /** 加入歌单的歌曲 ID 非法 */
    public static final int PLAYLIST_SONG_INVALID = 40013;

    /** 评论正文为空或超长 */
    public static final int COMMENT_CONTENT_INVALID = 40014;

    /** 回复的目标评论不存在或不可见 */
    public static final int COMMENT_PARENT_INVALID = 40015;

    /** 音频文件类型不在上传白名单内 */
    public static final int AUDIO_FORMAT_NOT_ALLOWED = 40016;

    /** 封面图片类型不在上传白名单内 */
    public static final int COVER_FORMAT_NOT_ALLOWED = 40017;

    /** 上传文件超过大小上限 */
    public static final int UPLOAD_FILE_TOO_LARGE = 40018;

    /** 上传文件不存在、已删除或不可用 */
    public static final int UPLOAD_FILE_INVALID = 40019;

    /** 上传未声明作品来源（版权合规必填） */
    public static final int COPYRIGHT_SOURCE_REQUIRED = 40020;

    /** 上传来源取值非法 */
    public static final int COPYRIGHT_SOURCE_INVALID = 40021;

    /** 申诉理由为空或超长（复审意见同理） */
    public static final int APPEAL_REASON_INVALID = 40022;

    /** 不能操作他人作品 */
    public static final int SONG_NOT_OWNED = 40301;

    /** 播放票据无效、已过期或与当前用户/歌曲不匹配 */
    public static final int PLAY_TICKET_INVALID = 40302;

    /** 不能操作他人歌单 */
    public static final int PLAYLIST_NOT_OWNED = 40303;

    /** 官方歌单用户侧只读 */
    public static final int PLAYLIST_OFFICIAL_READONLY = 40304;

    /** 歌手不存在 */
    public static final int ARTIST_NOT_FOUND = 40401;

    /** 专辑不存在 */
    public static final int ALBUM_NOT_FOUND = 40402;

    /** 歌曲不存在或不可见 */
    public static final int SONG_NOT_FOUND = 40403;

    /** 标签不存在 */
    public static final int TAG_NOT_FOUND = 40404;

    /** 歌单不存在或不可见 */
    public static final int PLAYLIST_NOT_FOUND = 40405;

    /** 评论不存在 */
    public static final int COMMENT_NOT_FOUND = 40406;

    /** 申诉不存在 */
    public static final int APPEAL_NOT_FOUND = 40407;

    /** 歌曲当前状态不允许该操作 */
    public static final int SONG_STATE_CONFLICT = 40901;

    /** 申诉已复核，不可重复流转 */
    public static final int APPEAL_STATE_CONFLICT = 40902;

    /** 歌曲不在版权下架状态，不允许申诉或恢复 */
    public static final int APPEAL_NOT_ALLOWED = 40903;

    /** 错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(ARTIST_NAME_DUPLICATED, "同一租户内歌手名称已存在"),
            Map.entry(TAG_DUPLICATED, "同一租户内标签名称与类型已存在"),
            Map.entry(ALBUM_ARTIST_INVALID, "专辑关联的歌手不存在或已停用"),
            Map.entry(SONG_NOT_READY, "歌曲信息不完整，暂不能发布"),
            Map.entry(TAKEDOWN_REASON_REQUIRED, "版权下架必须填写原因"),
            Map.entry(AGREEMENT_REQUIRED, "请先签署当前版本的版权无争议协议"),
            Map.entry(SONG_TITLE_INVALID, "歌曲标题为空或超过 120 个字符"),
            Map.entry(AGREEMENT_VERSION_MISMATCH, "协议版本已更新，请刷新后重新签署"),
            Map.entry(PLAY_PROGRESS_INVALID, "播放进度参数非法"),
            Map.entry(PLAYLIST_TITLE_INVALID, "歌单标题为空或超过 120 个字符"),
            Map.entry(PLAYLIST_SONG_LIMIT, "歌单歌曲数量已达上限"),
            Map.entry(PLAYLIST_SONG_ORDER_INVALID, "歌单歌曲顺序非法或与歌单内容不一致"),
            Map.entry(PLAYLIST_SONG_INVALID, "加入歌单的歌曲 ID 非法"),
            Map.entry(COMMENT_CONTENT_INVALID, "评论正文为空或超过 500 个字符"),
            Map.entry(COMMENT_PARENT_INVALID, "回复的评论不存在或不可见"),
            Map.entry(AUDIO_FORMAT_NOT_ALLOWED, "仅支持 MP3 / AAC / FLAC 音频文件"),
            Map.entry(COVER_FORMAT_NOT_ALLOWED, "封面仅支持 JPEG / PNG / WebP 图片"),
            Map.entry(UPLOAD_FILE_TOO_LARGE, "上传文件超过大小上限"),
            Map.entry(UPLOAD_FILE_INVALID, "上传文件不存在或不可用，请重新上传"),
            Map.entry(COPYRIGHT_SOURCE_REQUIRED, "请先声明作品来源（原创 / 已授权 / 无版权争议素材）"),
            Map.entry(COPYRIGHT_SOURCE_INVALID, "作品来源取值非法"),
            Map.entry(APPEAL_REASON_INVALID, "申诉理由为空或超过 500 个字符"),
            Map.entry(SONG_NOT_OWNED, "无权操作他人的作品"),
            Map.entry(PLAY_TICKET_INVALID, "播放凭证无效或已过期，请重新发起播放"),
            Map.entry(PLAYLIST_NOT_OWNED, "无权操作他人的歌单"),
            Map.entry(PLAYLIST_OFFICIAL_READONLY, "官方歌单仅运营可维护，用户侧只读"),
            Map.entry(ARTIST_NOT_FOUND, "歌手不存在"),
            Map.entry(ALBUM_NOT_FOUND, "专辑不存在"),
            Map.entry(SONG_NOT_FOUND, "歌曲不存在或无权访问"),
            Map.entry(TAG_NOT_FOUND, "标签不存在"),
            Map.entry(PLAYLIST_NOT_FOUND, "歌单不存在或无权访问"),
            Map.entry(COMMENT_NOT_FOUND, "评论不存在"),
            Map.entry(APPEAL_NOT_FOUND, "申诉记录不存在"),
            Map.entry(SONG_STATE_CONFLICT, "歌曲当前状态不允许该操作"),
            Map.entry(APPEAL_STATE_CONFLICT, "申诉已复核，不可重复处理"),
            Map.entry(APPEAL_NOT_ALLOWED, "作品不在版权下架状态，不支持申诉或恢复"));

    /**
     * 私有构造器：错误码常量类，禁止实例化。
     */
    private MusicErrorCode() {
    }

    /**
     * 查询错误码默认提示语。
     *
     * @param code 业务错误码
     * @return 默认提示语；未知码返回通用失败文案
     */
    public static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "音乐操作失败");
    }

    /**
     * 以错误码构造业务异常。
     *
     * @param code 业务错误码
     * @return 业务异常
     */
    public static BizException exception(int code) {
        return new BizException(code, messageOf(code));
    }
}
