package com.bluewhale.map.provider;

/**
 * 地址解析查询条件。
 *
 * @param address 结构化地址（必填，1–200 字）
 * @param city    限定城市（可空；同名地点在不同城市很常见，如「中山路」）
 * @author BlueWhale
 */
public record GeocodeQuery(String address, String city) {

    /** 地址长度上限：超长地址在供应商侧几乎必然无结果，提前拒绝省一次配额 */
    private static final int MAX_ADDRESS_LENGTH = 200;

    /**
     * 校验并构造查询条件
     *
     * @param address 结构化地址
     * @param city    限定城市
     */
    public GeocodeQuery {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("地址不能为空");
        }
        address = address.trim();
        if (address.length() > MAX_ADDRESS_LENGTH) {
            throw new IllegalArgumentException("地址过长（上限 " + MAX_ADDRESS_LENGTH + " 字）");
        }
        city = city == null || city.isBlank() ? null : city.trim();
    }
}
