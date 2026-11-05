package com.bluewhale.pdm.process.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * MES 工艺路线下发请求（内网契约 {@code POST /mes/internal/process-route}）。
 *
 * <p>幂等键为 {@code routeNo + versionNo}；{@code checksum} 由 PDM 计算，
 * 内容未变化时重复下发返回既有记录。</p>
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcMesPublishRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工艺路线编号（幂等键） */
    private String routeNo;

    /** 版本号（幂等键） */
    private String versionNo;

    /** 零件编码 */
    private String partCode;

    /** 零件名称 */
    private String partName;

    /** 下发内容校验和 */
    private String checksum;

    /** 工序列表 */
    private List<Step> steps = new ArrayList<>();

    /**
     * 下发工序。
     */
    @Data
    public static class Step implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 工序序号 */
        private Integer stepNo;

        /** 工序名称 */
        private String name;

        /** 工作中心 */
        private String workCenter;

        /** 标准工时 */
        private BigDecimal standardHours;

        /** 准备工时 */
        private BigDecimal setupHours;

        /** 工序说明 */
        private String description;

        /** 工装编码列表 */
        private List<Tooling> toolings = new ArrayList<>();

        /** SOP 文档ID */
        private Long sopDocumentId;

        /** SOP 文档版本号 */
        private String sopDocumentVersionNo;
    }

    /**
     * 下发工装。
     */
    @Data
    public static class Tooling implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 类型 */
        private String toolingType;

        /** 工装编码 */
        private String toolingCode;

        /** 规格 */
        private String spec;

        /** 数量 */
        private Integer quantity;
    }
}
