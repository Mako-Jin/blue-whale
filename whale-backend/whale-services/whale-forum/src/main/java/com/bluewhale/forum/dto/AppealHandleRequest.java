package com.bluewhale.forum.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 复核申诉请求。
 *
 * @author BlueWhale
 */
@Data
public class AppealHandleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否通过：true 通过 / false 驳回 */
    @NotNull(message = "{validation.forum.appeal.conclusion-required}")
    private Boolean approved;

    /** 复核意见 */
    @Size(max = 500, message = "{validation.forum.appeal.remark-size}")
    private String remark;
}
