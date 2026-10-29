package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 页面保存结果。
 *
 * @author BlueWhale
 */
@Data
public class DocPageSaveVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long id;

    /** 保存后的 revision */
    private Long revision;

    /** 页面状态 */
    private String status;

    /** 保存时间 */
    private LocalDateTime savedAt;
}
