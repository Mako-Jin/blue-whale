package com.bluewhale.video.domain.model;

/**
 * 评论状态：待审 / 可见 / 隐藏。
 *
 * <p>默认先发后审：新评论直接 {@link #VISIBLE} 并进入审核队列；
 * 运营隐藏后用户侧立即不可见，评论数同步扣减。</p>
 *
 * @author BlueWhale
 */
public enum CommentStatus {

    /** 待审核（先审后发模式下新评论的初始状态） */
    PENDING,

    /** 可见 */
    VISIBLE,

    /** 已隐藏（审核不通过或运营隐藏） */
    HIDDEN;

    /**
     * 解析评论状态文本。
     *
     * @param value 存储文本
     * @return 评论状态；无法识别回落 {@link #VISIBLE}
     */
    public static CommentStatus parse(String value) {
        for (CommentStatus item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return VISIBLE;
    }
}
