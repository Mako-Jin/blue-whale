package com.bluewhale.video.infrastructure.persistence.query;

import lombok.Data;

/**
 * 内容-标签关系行（{@code vd_content_tag} 投影）。
 *
 * @author BlueWhale
 */
@Data
public class ContentTagRow {

    /** 内容 ID */
    private Long contentId;

    /** 标签 ID */
    private Long tagId;
}
