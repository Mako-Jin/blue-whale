package com.bluewhale.video.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 系列详情视图：在系列基础信息上补充已发布集数列表。
 *
 * <p>用户侧只展示已上架且公开的集数，按集数升序返回。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SeriesDetailView extends SeriesView {

    private static final long serialVersionUID = 1L;

    /** 已发布集数列表 */
    private List<PublicContentView> episodes;
}
