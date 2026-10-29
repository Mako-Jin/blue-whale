package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手动保存版本快照请求。
 *
 * @author BlueWhale
 */
@Data
public class DocVersionManualRequest {

    /** 变更说明 */
    @Size(max = 200, message = "变更说明不能超过 200 字")
    private String changeSummary;
}
