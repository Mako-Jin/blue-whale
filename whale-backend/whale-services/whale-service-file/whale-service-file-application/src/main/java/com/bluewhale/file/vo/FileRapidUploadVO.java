package com.bluewhale.file.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒传结果视图对象：命中时返回复用物理对象的新元数据，未命中时 {@code hit=false} 且 {@code file=null}。
 *
 * @author BlueWhale
 */
@Data
public class FileRapidUploadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否命中已有物理对象 */
    private boolean hit;

    /** 命中时登记的元数据；未命中时为 {@code null}，由客户端转普通上传 */
    private FileUploadVO file;
}
