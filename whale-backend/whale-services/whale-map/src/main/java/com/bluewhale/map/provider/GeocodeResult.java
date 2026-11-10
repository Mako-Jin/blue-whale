package com.bluewhale.map.provider;

import com.bluewhale.map.common.coord.GeoPoint;

/**
 * 地址解析结果。
 *
 * @param point            坐标（恒为 WGS84）
 * @param formattedAddress 供应商返回的标准地址
 * @param province         省
 * @param city             市
 * @param district         区县
 * @param adcode           行政区划码（六位，用于与 ERP/OA 的组织归属对齐）
 * @param level            匹配级别（如「门址」「道路」「区县」），用于判断精度是否够用
 * @param provider         实际提供结果的供应商名
 * @author BlueWhale
 */
public record GeocodeResult(GeoPoint point, String formattedAddress, String province, String city,
                            String district, String adcode, String level, String provider) {
}
