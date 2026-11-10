package com.bluewhale.twin.dto;

import lombok.Data;

/**
 * AI 解读请求（孪生查看器「AI 解读」入口）。
 *
 * @author BlueWhale
 */
@Data
public class InsightRequest {

    /** 解读目标实体ID（可空 = 整场景概要解读） */
    private Long entityId;

    /** 用户追问（可空 = 默认「解读当前状态并给出风险提示」） */
    private String question;
}
