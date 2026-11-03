package com.bluewhale.video.domain.exception;

import com.bluewhale.common.core.exception.BizException;

import java.util.Map;

/**
 * 视频服务业务错误码（M1 起返回，HTTP 状态由全局异常处理器映射）。
 *
 * <p>段位约定：{@code 400xx} 业务校验失败，{@code 403xx} 数据权限不足，
 * {@code 404xx} 资源不存在，{@code 409xx} 状态冲突。跨租户资源统一返回
 * 「不存在」语义，不泄露存在性。</p>
 *
 * @author BlueWhale
 */
public final class VideoErrorCode {

    /** 内容标题为空或超长 */
    public static final int CONTENT_TITLE_INVALID = 40001;

    /** 内容时长非法（超出类型上限或为 0） */
    public static final int CONTENT_DURATION_INVALID = 40002;

    /** 视频文件类型不在上传白名单内 */
    public static final int VIDEO_FORMAT_NOT_ALLOWED = 40003;

    /** 封面 / 海报图片类型不在上传白名单内 */
    public static final int IMAGE_FORMAT_NOT_ALLOWED = 40004;

    /** 上传文件超过类型大小上限 */
    public static final int UPLOAD_FILE_TOO_LARGE = 40005;

    /** 上传文件不存在、已删除或不可用 */
    public static final int UPLOAD_FILE_INVALID = 40006;

    /** 未签署当前版本的内容合法与版权无争议声明 */
    public static final int DECLARATION_REQUIRED = 40007;

    /** 声明版本与服务端当前版本不一致 */
    public static final int DECLARATION_VERSION_MISMATCH = 40008;

    /** 上传未声明内容来源 */
    public static final int CONTENT_SOURCE_REQUIRED = 40009;

    /** 内容来源取值非法 */
    public static final int CONTENT_SOURCE_INVALID = 40010;

    /** 电影授权来源 / 授权编号 / 到期时间缺失或非法 */
    public static final int MOVIE_LICENSE_INVALID = 40011;

    /** 分类编码或名称非法 */
    public static final int CATEGORY_INVALID = 40012;

    /** 系列集数非法（重复或越界） */
    public static final int EPISODE_NO_INVALID = 40013;

    /** 评论正文为空或超长 */
    public static final int COMMENT_CONTENT_INVALID = 40014;

    /** 回复的目标评论不存在或不可见 */
    public static final int COMMENT_PARENT_INVALID = 40015;

    /** 观看进度参数非法 */
    public static final int PLAY_PROGRESS_INVALID = 40016;

    /** 版权下架必须填写原因 */
    public static final int TAKEDOWN_REASON_REQUIRED = 40017;

    /** 举报理由非法 */
    public static final int REPORT_REASON_INVALID = 40018;

    /** 不能操作他人的内容 */
    public static final int CONTENT_NOT_OWNED = 40301;

    /** 播放票据无效、已过期或与当前用户 / 内容不匹配 */
    public static final int PLAY_TICKET_INVALID = 40302;

    /** 内容当前不可播放（未发布 / 私密他人 / 版权下架 / 授权到期） */
    public static final int CONTENT_NOT_PLAYABLE = 40303;

    /** 内容不存在或无权访问（跨租户一律按不存在处理） */
    public static final int CONTENT_NOT_FOUND = 40401;

    /** 分类不存在 */
    public static final int CATEGORY_NOT_FOUND = 40402;

    /** 标签不存在 */
    public static final int TAG_NOT_FOUND = 40403;

    /** 系列不存在 */
    public static final int SERIES_NOT_FOUND = 40404;

    /** 片单不存在 */
    public static final int COLLECTION_NOT_FOUND = 40405;

    /** 评论不存在 */
    public static final int COMMENT_NOT_FOUND = 40406;

    /** 创作者不存在 */
    public static final int CREATOR_NOT_FOUND = 40407;

    /** 内容当前状态不允许该操作 */
    public static final int CONTENT_STATE_CONFLICT = 40901;

    /** 系列集数冲突 */
    public static final int EPISODE_CONFLICT = 40902;

    /** 片单加片重复或顺序冲突 */
    public static final int COLLECTION_ITEM_CONFLICT = 40903;

    /** 错误码与默认提示语 */
    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(CONTENT_TITLE_INVALID, "标题为空或超过 200 个字符"),
            Map.entry(CONTENT_DURATION_INVALID, "内容时长非法或超出该类型上限"),
            Map.entry(VIDEO_FORMAT_NOT_ALLOWED, "仅支持 MP4 / WebM 视频文件"),
            Map.entry(IMAGE_FORMAT_NOT_ALLOWED, "封面仅支持 JPEG / PNG / WebP 图片"),
            Map.entry(UPLOAD_FILE_TOO_LARGE, "上传文件超过该类型大小上限"),
            Map.entry(UPLOAD_FILE_INVALID, "上传文件不存在或不可用，请重新上传"),
            Map.entry(DECLARATION_REQUIRED, "请先签署当前版本的内容合法与版权无争议声明"),
            Map.entry(DECLARATION_VERSION_MISMATCH, "声明版本已更新，请刷新后重新签署"),
            Map.entry(CONTENT_SOURCE_REQUIRED, "请先声明内容来源（原创 / 已授权 / 无版权争议素材）"),
            Map.entry(CONTENT_SOURCE_INVALID, "内容来源取值非法"),
            Map.entry(MOVIE_LICENSE_INVALID, "电影授权来源、授权编号或到期时间不合法"),
            Map.entry(CATEGORY_INVALID, "分类编码或名称非法"),
            Map.entry(EPISODE_NO_INVALID, "系列集数非法或重复"),
            Map.entry(COMMENT_CONTENT_INVALID, "评论正文为空或超过 500 个字符"),
            Map.entry(COMMENT_PARENT_INVALID, "回复的评论不存在或不可见"),
            Map.entry(PLAY_PROGRESS_INVALID, "观看进度参数非法"),
            Map.entry(TAKEDOWN_REASON_REQUIRED, "版权下架必须填写原因"),
            Map.entry(REPORT_REASON_INVALID, "举报理由为空或超过 500 个字符"),
            Map.entry(CONTENT_NOT_OWNED, "无权操作他人的内容"),
            Map.entry(PLAY_TICKET_INVALID, "播放凭证无效或已过期，请重新发起播放"),
            Map.entry(CONTENT_NOT_PLAYABLE, "内容当前不可播放"),
            Map.entry(CONTENT_NOT_FOUND, "内容不存在或无权访问"),
            Map.entry(CATEGORY_NOT_FOUND, "分类不存在"),
            Map.entry(TAG_NOT_FOUND, "标签不存在"),
            Map.entry(SERIES_NOT_FOUND, "系列不存在"),
            Map.entry(COLLECTION_NOT_FOUND, "片单不存在"),
            Map.entry(COMMENT_NOT_FOUND, "评论不存在"),
            Map.entry(CREATOR_NOT_FOUND, "创作者不存在"),
            Map.entry(CONTENT_STATE_CONFLICT, "内容当前状态不允许该操作"),
            Map.entry(EPISODE_CONFLICT, "系列集数冲突"),
            Map.entry(COLLECTION_ITEM_CONFLICT, "片单条目重复或顺序冲突"));

    /**
     * 私有构造器：错误码常量类，禁止实例化。
     */
    private VideoErrorCode() {
    }

    /**
     * 查询错误码默认提示语。
     *
     * @param code 业务错误码
     * @return 默认提示语；未知码返回通用失败文案
     */
    public static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "视频操作失败");
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
