package com.bluewhale.music.domain.model;

/**
 * 版权下架申诉状态。
 *
 * <p>{@link #PENDING} 表示上传者已提交申诉、等待运营复核；{@link #APPROVED}
 * 表示运营认可申诉、歌曲版权恢复为有效并转为下架待重新上架；
 * {@link #REJECTED} 表示申诉被驳回，歌曲保持版权下架。</p>
 *
 * @author BlueWhale
 */
public enum AppealStatus {

    /** 待复核 */
    PENDING,

    /** 申诉通过 */
    APPROVED,

    /** 申诉驳回 */
    REJECTED
}
