package com.bluewhale.file.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒传响应。
 *
 * @author BlueWhale
 */
@Data
public class FileRapidUploadResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean hit;
    private FileUploadResponse file;
}
