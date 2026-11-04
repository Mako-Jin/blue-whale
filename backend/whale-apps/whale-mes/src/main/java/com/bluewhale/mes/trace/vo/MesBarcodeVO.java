package com.bluewhale.mes.trace.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 条码视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesBarcodeVO {

    /** 条码 ID */
    private Long id;

    /** 条码值 */
    private String barcodeValue;

    /** 码制（来自规则） */
    private String codeType;

    /** 批次 ID */
    private Long batchId;

    /** 批次号 */
    private String batchNo;

    /** 物料编码 */
    private String itemCode;

    /** 绑定对象类型 */
    private String objectType;

    /** 状态 */
    private String status;

    /** 打印载荷 */
    private String printPayload;

    /** 最近扫码时间 */
    private LocalDateTime lastScanTime;

    /** 累计扫码次数 */
    private Integer scanCount;

    /** 创建时间 */
    private LocalDateTime createTime;
}
