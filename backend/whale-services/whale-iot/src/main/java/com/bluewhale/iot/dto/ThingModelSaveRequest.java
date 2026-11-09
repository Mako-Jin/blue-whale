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
    @NotNull(message = "条目列表不能为 null（传空数组表示清空）")
    @Size(max = 200, message = "物模型条目数不能超过 200")
    @Valid
    private List<ThingModelItem> items;
}
