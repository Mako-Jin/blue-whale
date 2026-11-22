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
    @Size(max = 255, message = "{validation.mes.mes-trace-reverse.remark-size}")
    private String remark;
}
