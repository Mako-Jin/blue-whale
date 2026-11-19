package com.bluewhale.map.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 地址解析请求。
 *
 * @author BlueWhale
 */
@Data
public class GeocodeRequest {

    /** 结构化地址 */
    @NotBlank(message = "{validation.map.geocode.address-required}")
    @Size(max = 200, message = "{validation.map.geocode.address-max}")
    private String address;

    /** 限定城市（可空） */
    @Size(max = 64, message = "{validation.map.geocode.city-max}")
    private String city;
}
