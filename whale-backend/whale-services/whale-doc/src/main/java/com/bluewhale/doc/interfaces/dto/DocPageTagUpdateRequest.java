package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 更新页面标签请求（全量替换）。
 *
 * @author BlueWhale
 */
@Data
public class DocPageTagUpdateRequest {

    /** 标签ID列表 */
    @Size(max = 20, message = "单页标签不能超过 20 个")
    private List<Long> tagIds = new ArrayList<>();
}
