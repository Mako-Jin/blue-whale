package com.bluewhale.mes.process.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * MES 接收工艺路线请求
 *
 * <p>契约来源：{@code POST /mes/internal/process-route}；
 * 幂等键为 {@code routeNo + versionNo}。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesProcessRouteReceiveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** PDM 工艺路线编号 */
    @NotBlank(message = "{validation.mes.mes-process-route-receive.route-no-required}")
    private String routeNo;

    /** PDM 工艺路线版本号 */
    @NotBlank(message = "{validation.mes.mes-process-route-receive.version-no-required}")
    private String versionNo;

    /** 零件编码 */
    private String partCode;

    /** 零件名称 */
    private String partName;

    /** 内容校验和 */
    private String checksum;

    /** 工序列表 */
    @Valid
    @NotNull(message = "{validation.mes.mes-process-route-receive.steps-required}")
    private List<Step> steps = new ArrayList<>();

    /**
     * 接收工序。
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

        /** 工装列表 */
        private List<Tooling> toolings = new ArrayList<>();

        /** SOP 文档ID */
        private Long sopDocumentId;

        /** SOP 文档版本号 */
        private String sopDocumentVersionNo;
    }

    /**
     * 接收工装。
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
