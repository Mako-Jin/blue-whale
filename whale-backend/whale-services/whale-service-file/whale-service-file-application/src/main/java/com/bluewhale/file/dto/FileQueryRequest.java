package com.bluewhale.file.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 文件信息分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 原始文件名（模糊匹配） */
    private String originalName;

    /** 对象存储桶名称（精确匹配） */
    private String bucket;

    /** 创建时间起（含），格式 {@code yyyy-MM-dd HH:mm:ss} */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeStart;

    /** 创建时间止（含），格式 {@code yyyy-MM-dd HH:mm:ss} */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeEnd;
}
