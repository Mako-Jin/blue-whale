package com.bluewhale.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 拒签请求：原因必填。
 *
 * @author BlueWhale
 */
@Data
public class SignRefuseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 拒签原因 */
    @NotBlank(message = "拒签原因不能为空")
    @Size(max = 255, message = "拒签原因长度不能超过 255")
    private String reason;
}
