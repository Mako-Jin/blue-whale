package com.bluewhale.map.vo;

import lombok.Data;

/**
 * 批量距离结果视图。
 *
 * @author BlueWhale
 */
@Data
public class DistanceVO {

    /** 目标点在请求数组中的下标（批量结果按下标对齐，避免靠顺序猜） */
    private int index;

    /** 直线距离（米） */
    private double distanceMeters;
}
