package com.bluewhale.twin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 场景发布请求（发布说明可空）。
 *
 * @author BlueWhale
 */
@Data
public class PublishRequest {

    /** 发布说明（最长 256 字符） */
    @Size(max = 256, message = "发布说明最长 256 字符")
    private String remark;
}
