package com.bluewhale.mes.trace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 扫码校验请求
 *
 * @author BlueWhale
 */
@Data
public class MesScanValidateRequest {

    /** 条码值 */
    @NotBlank(message = "{validation.mes.mes-scan-validate.barcode-value-required}")
    @Size(max = 128, message = "{validation.mes.mes-scan-validate.barcode-value-size}")
    private String barcodeValue;

    /** 工位编码 */
    @Size(max = 64, message = "{validation.mes.mes-scan-validate.station-code-size}")
    private String stationCode;

    /** 工序编码 */
    @Size(max = 64, message = "{validation.mes.mes-scan-validate.operation-code-size}")
    private String operationCode;

    /** 扫码幂等键（相同键重复扫码不产生副作用） */
    @Size(max = 64, message = "{validation.mes.mes-scan-validate.scan-no-size}")
    private String scanNo;

    /** 关联任务 ID */
    private Long taskId;
}
