package com.bluewhale.archive.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 表单归档详情（跨服务内网响应体）。
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteFormArchive {

    /** 表单数据 */
    private RemoteFormData data;

    /** 字段定义 */
    private List<RemoteFormField> fieldDefinitions;
}
