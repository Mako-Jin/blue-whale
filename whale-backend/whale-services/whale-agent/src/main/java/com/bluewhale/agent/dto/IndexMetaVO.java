package com.bluewhale.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 索引元数据快照（状态接口装配用，避免 controller 依赖实体，T-18）。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class IndexMetaVO {

    /** 索引版本 */
    private String indexVersion;

    /** 最近构建时间 */
    private LocalDateTime lastBuildAt;
}
