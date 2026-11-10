package com.bluewhale.video.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * feed 游标：由排序锚点与内容 ID 组成的不可变键集定位值。
 *
 * <p>编码形如 {@code anchor|id}：最新流锚点为 {@code publishedAt} 的毫秒时间戳，
 * 热门流锚点为 {@code hot_score} 十进制字符串；解析失败统一返回参数错误（400）。</p>
 *
 * @author BlueWhale
 */
public final class FeedCursor {

    /** 游标最大长度，防止异常入参过长 */
    private static final int MAX_LENGTH = 64;

    /** 游标分隔符 */
    private static final char SEPARATOR = '|';

    /** 锚点格式：整数或十进制小数 */
    private static final Pattern ANCHOR_PATTERN = Pattern.compile("\\d+(\\.\\d+)?");

    /** 非法游标提示语 */
    private static final String INVALID_MESSAGE = "feed 游标非法，请重新加载";

    /** 排序锚点 */
    private final String anchor;

    /** 内容 ID（同一锚点内的次排序键） */
    private final long id;

    /**
     * 私有构造器：统一由 {@link #of(String, long)} 或 {@link #parse(String)} 创建。
     *
     * @param anchor 排序锚点
     * @param id 内容 ID
     */
    private FeedCursor(String anchor, long id) {
        this.anchor = anchor;
        this.id = id;
    }

    /**
     * 创建游标。
     *
     * @param anchor 排序锚点
     * @param id 内容 ID
     * @return 游标
     */
    public static FeedCursor of(String anchor, long id) {
        return new FeedCursor(anchor, id);
    }

    /**
     * 解析客户端回传的游标。
     *
     * @param raw 原始游标；空白视为首页
     * @return 游标；入参空白返回 {@code null}
     */
    public static FeedCursor parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        int index = text.indexOf(SEPARATOR);
        if (text.length() > MAX_LENGTH || index <= 0 || index >= text.length() - 1) {
            throw invalid();
        }
        String anchor = text.substring(0, index);
        if (!ANCHOR_PATTERN.matcher(anchor).matches()) {
            throw invalid();
        }
        try {
            long contentId = Long.parseLong(text.substring(index + 1));
            if (contentId <= 0) {
                throw invalid();
            }
            return new FeedCursor(anchor, contentId);
        } catch (NumberFormatException ex) {
            throw invalid();
        }
    }

    /**
     * 编码为客户端可回传的游标字符串。
     *
     * @return 形如 {@code anchor|id} 的游标
     */
    public String toValue() {
        return anchor + SEPARATOR + id;
    }

    /**
     * 读取排序锚点。
     *
     * @return 排序锚点
     */
    public String getAnchor() {
        return anchor;
    }

    /**
     * 读取内容 ID。
     *
     * @return 内容 ID
     */
    public long getId() {
        return id;
    }

    /**
     * 构造参数错误异常。
     *
     * @return 参数错误业务异常
     */
    private static BizException invalid() {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), INVALID_MESSAGE);
    }
}
