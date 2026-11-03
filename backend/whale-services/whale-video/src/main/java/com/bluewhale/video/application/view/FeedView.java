package com.bluewhale.video.application.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 短视频 feed 键集响应（游标分页）。
 *
 * <p>{@code nextCursor} 为空表示没有下一页；客户端把 {@code nextCursor} 原样回传即可续读，
 * 避免页码分页在发布 / 下架时出现重复或漏项。</p>
 *
 * @author BlueWhale
 */
@Data
public class FeedView {

    /** 当前批次内容（已按可见性过滤，不含任何文件 ID） */
    private List<PublicContentView> records = new ArrayList<>();

    /** 下一页游标；无下一页时为 {@code null} */
    private String nextCursor;

    /** 是否还有下一页 */
    private boolean hasMore;
}
