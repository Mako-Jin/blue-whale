package com.bluewhale.mes.trace.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 谱系链接冲销请求。
 *
 * @author BlueWhale
 */
@Data
public class MesTraceReverseRequest {

    /** 冲销原因 */
    @Size(max = 255, message = "冲销原因长度不能超过 255")
    private String remark;
}
