package com.bluewhale.form.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 表定义视图对象（表定义层，含列集合）。
 *
 * @author BlueWhale
 */
@Data
public class FormTableDefinitionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 表编码 */
    private String tableCode;

    /** 表名称 */
    private String tableName;

    /** 版本号 */
    private Integer version;

    /** 备注 */
    private String remark;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 列集合 */
    private List<FormColumnMetaVO> columns;
}
