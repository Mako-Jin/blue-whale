package com.bluewhale.oa.culture.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 建议实体，对应表 {@code oa_suggestion}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_suggestion")
public class SuggestionEntity extends BaseEntity {

    private String title;

    private String category;

    private String content;

    private Integer anonymous;

    private Long submitterId;

    private String submitterName;

    private Long deptId;

    private String status;

    private Long handlerId;

    private String handlerName;

    private LocalDateTime handledTime;
}
