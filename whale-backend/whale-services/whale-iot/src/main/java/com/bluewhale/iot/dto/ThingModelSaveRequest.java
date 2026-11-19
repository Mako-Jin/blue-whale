package com.bluewhale.iot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 物模型整包保存请求（覆盖产品下全部条目）。
 *
 * @author BlueWhale
 */
@Data
public class ThingModelSaveRequest {

    /** 物模型条目全集（可为空列表 = 清空物模型；条目数上限 200） */
    @NotNull(message = "{validation.iot.thing-model.list-required}")
    @Size(max = 200, message = "{validation.iot.thing-model.list-size}")
    @Valid
    private List<ThingModelItem> items;
}
