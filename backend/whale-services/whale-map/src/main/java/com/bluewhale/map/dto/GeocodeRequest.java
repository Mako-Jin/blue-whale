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
    @NotBlank(message = "地址不能为空")
    @Size(max = 200, message = "地址不能超过 200 字")
    private String address;

    /** 限定城市（可空） */
    @Size(max = 64, message = "城市名过长")
    private String city;
}
