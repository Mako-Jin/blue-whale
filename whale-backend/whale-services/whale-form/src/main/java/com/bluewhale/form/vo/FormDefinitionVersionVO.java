package com.bluewhale.form.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单定义版本快照视图对象。
 *
 * @author BlueWhale
 */
@Data
public class FormDefinitionVersionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版本号 */
    private Integer version;

    /** 快照序号（同一表单定义内单调递增） */
    private Long snapshotSeq;

    /** 快照创建时间 */
    private LocalDateTime createTime;
}
