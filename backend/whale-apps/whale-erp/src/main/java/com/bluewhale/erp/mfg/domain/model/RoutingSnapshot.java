package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PDM 已发布工艺路线快照。
 *
 * @author BlueWhale
 */
@Data
public class RoutingSnapshot {

    /** 工艺路线编号 */
    private String routeNo;

    /** 版本号 */
    private String versionNo;

    /** 零件编码 */
    private String partCode;

    /** 零件名称 */
    private String partName;

    /** 发布时间 */
    private LocalDateTime releasedAt;

    /** 工序（序号升序） */
    private List<RoutingStep> steps;
}
