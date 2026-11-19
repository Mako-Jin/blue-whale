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
    @Size(max = 200, message = "{validation.doc.version.change-summary-size}")
    private String changeSummary;
}
