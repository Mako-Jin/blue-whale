package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程实例归档详情视图对象（档案同步内网接口契约）。
 *
 * <p>一次返回实例、定义快照 JSON 与任务轨迹，供档案服务生成不可变快照；
 * {@code definitionJson} 优先取实例所用版本的定义快照，缺失时回落当前定义最新内容。</p>
 *
 * @author BlueWhale
 */
@Data
public class ArchiveInstanceDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实例ID */
    private Long id;

    /** 流程定义 ID */
    private Long definitionId;

    /** 流程定义编码 */
    private String definitionCode;

    /** 流程定义版本 */
    private Integer definitionVersion;

    /** 实例标题 */
    private String title;

    /** 业务键 */
    private String businessKey;

    /** 实例状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;

    /** 发起人账号 */
    private String initiator;

    /** 流程变量 JSON */
    private String variablesJson;

    /** 定义快照 JSON（模型定义原文） */
    private String definitionJson;

    /** 办结时间（取实例更新时间） */
    private LocalDateTime finishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 任务轨迹（按节点下标、创建时间升序） */
    private List<ProcessTaskVO> tasks;
}
