package com.bluewhale.video.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上传声明视图。
 *
 * <p>{@code accepted} 为当前用户是否已签署服务端当前版本。</p>
 *
 * @author BlueWhale
 */
@Data
public class DeclarationView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 声明版本 */
    private String version;

    /** 声明标题 */
    private String title;

    /** 声明正文（纯文本条款） */
    private String text;

    /** 当前用户是否已签署该版本 */
    private boolean accepted;

    /** 最近签署的内容来源 */
    private String contentSource;

    /** 最近签署时间 */
    private LocalDateTime acceptedAt;
}
