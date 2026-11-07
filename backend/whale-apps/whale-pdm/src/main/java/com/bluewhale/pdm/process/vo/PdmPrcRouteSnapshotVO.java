package com.bluewhale.pdm.process.vo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工艺路线发布快照（ERP 内网契约）。
 *
 * <p>只包含已发布路线的冻结字段，按工序序号升序；同一版本重复读取结果一致。</p>
 *
 * @author BlueWhale
 */
@Data
@JsonPropertyOrder({"routeNo", "versionNo", "partCode", "partName", "releasedAt", "steps"})
public class PdmPrcRouteSnapshotVO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 工序快照（序号升序） */
    private List<Step> steps = new ArrayList<>();

    /**
     * 工序快照。
     */
    @Data
    @JsonPropertyOrder({"stepNo", "name", "workCenter", "standardHours", "setupHours"})
    public static class Step implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 工序序号 */
        private Integer stepNo;

        /** 工序名称 */
        private String name;

        /** 工作中心 */
        private String workCenter;

        /** 标准工时（小时） */
        private BigDecimal standardHours;

        /** 准备工时（小时） */
        private BigDecimal setupHours;
    }
}
