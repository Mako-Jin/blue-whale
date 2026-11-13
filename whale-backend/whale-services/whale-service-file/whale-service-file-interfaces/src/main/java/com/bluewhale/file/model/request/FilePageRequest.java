package com.bluewhale.file.model.request;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 文件分页查询请求。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FilePageRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 原始文件名模糊条件 */
    private String originalName;

    /** 桶名称等值条件 */
    private String bucket;

    /** 创建时间起（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeStart;

    /** 创建时间止（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTimeEnd;

    /**
     * 是否仅查本人上传的文件（{@code true}=只看我的）。
     *
     * <p>默认 {@code false}：无管理权限者本来就只能看本人文件，持有
     * {@code sys:file:manage} 者可见全租户；显式传 {@code true} 供管理员
     * 在「我的文件」页只看自己上传的文件。</p>
     */
    private Boolean onlyMine;
}
