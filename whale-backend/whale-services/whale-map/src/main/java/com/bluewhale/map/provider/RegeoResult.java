package com.bluewhale.map.provider;

/**
 * 逆地理结果。
 *
 * @param formattedAddress 标准地址（可直接展示与落库）
 * @param province         省
 * @param city             市
 * @param district         区县
 * @param adcode           行政区划码
 * @param street           街道/道路
 * @param streetNumber     门牌号
 * @param provider         实际提供结果的供应商名
 * @author BlueWhale
 */
public record RegeoResult(String formattedAddress, String province, String city, String district,
                           String adcode, String street, String streetNumber, String provider) {
}
