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
    @NotBlank(message = "条码不能为空")
    @Size(max = 128, message = "条码长度不能超过 128")
    private String barcodeValue;

    /** 工位编码 */
    @Size(max = 64, message = "工位编码长度不能超过 64")
    private String stationCode;

    /** 工序编码 */
    @Size(max = 64, message = "工序编码长度不能超过 64")
    private String operationCode;

    /** 扫码幂等键（相同键重复扫码不产生副作用） */
    @Size(max = 64, message = "扫码幂等键长度不能超过 64")
    private String scanNo;

    /** 关联任务 ID */
    private Long taskId;
}
