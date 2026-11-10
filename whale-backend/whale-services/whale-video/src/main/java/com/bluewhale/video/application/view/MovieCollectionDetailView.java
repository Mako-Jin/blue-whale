package com.bluewhale.video.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 电影片单详情视图：在片单视图基础上附带片单内电影列表。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MovieCollectionDetailView extends MovieCollectionView {

    private static final long serialVersionUID = 1L;

    /** 片单内电影（按片单排序，仅返回当前可见内容） */
    private List<MovieView> items;
}
