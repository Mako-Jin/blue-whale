package com.bluewhale.mes.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * MES 工艺路线接收记录
 *
 * <p>以 {@code pdmRouteNo + versionNo} 幂等；完整下发内容以 JSON 原文保存，
 * 车间执行侧从此表读取有效路线。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_process_route")
public class MesProcessRoute extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** PDM 工艺路线编号 */
    private String pdmRouteNo;

    /** PDM 工艺路线版本号 */
    private String versionNo;

    /** 零件编码快照 */
    private String partCode;

    /** 零件名称快照 */
    private String partName;

    /** 状态：ACTIVE/WITHDRAWN */
    private String status;

    /** PDM 下发内容校验和 */
    private String checksum;

    /** 工序数量 */
    private Integer stepCount;

    /** 完整下发内容（JSON） */
    private String payload;

    /** PDM 下发时间 */
    private LocalDateTime pushedAt;

    /** PDM 撤回时间 */
    private LocalDateTime withdrawnAt;
}
