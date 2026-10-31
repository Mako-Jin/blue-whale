package com.bluewhale.music.domain.model;

/**
 * 歌曲版权状态。
 *
 * <p>{@link #DECLARED} 表示上传者已签署《版权无争议协议》并声明权利；
 * {@link #VALID} 表示运营复核通过；{@link #TAKEDOWN} 优先级最高，
 * 一旦置为版权下架，歌曲强制下线且拒绝签发播放票据。</p>
 *
 * @author BlueWhale
 */
public enum CopyrightState {

    /** 已声明：上传者已签署协议，等待复核 */
    DECLARED,

    /** 有效：运营复核通过，可用于上架与播放 */
    VALID,

    /** 版权下架：存在权利争议或被投诉，强制下线并拒绝播放 */
    TAKEDOWN;

    /**
     * 判断当前版权状态是否允许公开播放。
     *
     * @return {@code true} 表示可签发播放票据
     */
    public boolean canPlay() {
        return this != TAKEDOWN;
    }
}
