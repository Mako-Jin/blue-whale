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
    @NotBlank(message = "{validation.contract.sign-refuse.reason-required}")
    @Size(max = 255, message = "{validation.contract.sign-refuse.reason-size}")
    private String reason;
}
