package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.iot.dto.CommandCreateRequest;
import com.bluewhale.iot.dto.CommandVO;
import com.bluewhale.iot.entity.IotCommand;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.entity.IotThingModel;
import com.bluewhale.iot.mapper.IotCommandMapper;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotThingModelMapper;
import com.bluewhale.iot.support.EmqxPublishClient;
import com.bluewhale.iot.support.JsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 指令服务：下行指令落库、EMQX 投递、设备回执收敛与状态查询。
 *
 * <p><b>幂等</b>：{@code message_id} 唯一键，重复下发直接返回既有指令（不重复投递），
 * 与商城下单的幂等键口径一致。</p>
 *
 * <p><b>状态机</b>：{@code pending}（已落库）→ {@code sent}（Broker 已受理）→
 * {@code success}/{@code failed}（设备回执）。投递失败时保持 {@code pending}
 * 而非 {@code failed}——报文没出去就不该判定设备执行失败，留待 M3 重投扫描。</p>
 *
 * <p><b>期望值联动</b>：指令参数中与物模型属性同名的键会写入设备影子 desired，
 * 设备上线时由影子服务补推，形成「下发—期望—上报—收敛」闭环。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotCommandService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 主题前缀 */
    private static final String TOPIC_PREFIX = "bw/";

    /** 下行主题后缀 */
    private static final String TOPIC_DOWN_COMMAND = "/down/command";

    /** 状态：待下发 */
    public static final String STATUS_PENDING = "pending";

    /** 状态：已下发 */
    public static final String STATUS_SENT = "sent";

    /** 状态：成功 */
    public static final String STATUS_SUCCESS = "success";

    /** 状态：失败 */
    public static final String STATUS_FAILED = "failed";

    /** 状态：超时 */
    public static final String STATUS_TIMEOUT = "timeout";

    private final IotCommandMapper commandMapper;

    private final IotDeviceMapper deviceMapper;

    private final IotThingModelMapper thingModelMapper;

    private final IotProductService productService;

    private final IotShadowService shadowService;

    private final IotStreamService streamService;

    private final EmqxPublishClient publishClient;

    /**
     * 下发指令（落库 → 写影子期望值 → 经 EMQX 投递）。
     *
     * @param deviceId 设备ID
     * @param request  指令请求
     * @param issuedBy 下发人用户ID（可空）
     * @return 指令视图
     */
    public CommandVO create(Long deviceId, CommandCreateRequest request, Long issuedBy) {
        IotDevice device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "iot.device.not-found", "设备不存在");
        }
        if (!IotDeviceService.STATUS_ACTIVE.equals(device.getStatus())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "iot.command.device-disabled", "设备已禁用，不能下发指令");
        }
        String messageId = StringUtils.hasText(request.getMessageId())
                ? request.getMessageId() : UUID.randomUUID().toString().replace("-", "");
        IotCommand existed = commandMapper.selectOne(new LambdaQueryWrapper<IotCommand>()
                .eq(IotCommand::getMessageId, messageId)
                .last("LIMIT 1"));
        if (existed != null) {
            return toVo(existed);
        }
        validateIdentifier(device.getProductId(), request.getIdentifier());
        IotCommand command = new IotCommand();
        command.setDeviceId(deviceId);
        command.setIdentifier(request.getIdentifier());
        command.setParamsJson(request.getParams() == null ? null : JsonCodec.write(request.getParams()));
        command.setMessageId(messageId);
        command.setStatus(STATUS_PENDING);
        command.setIssuedBy(issuedBy);
        command.setIssuedTime(LocalDateTime.now());
        commandMapper.insert(command);

        markDesiredFromParams(device, request.getParams());
        String topic = topicOf(device, TOPIC_DOWN_COMMAND);
        if (publishClient.publish(topic, JsonCodec.write(downPayload(command)))) {
            command.setStatus(STATUS_SENT);
            commandMapper.updateById(command);
        } else {
            log.warn("指令未投递（保持 pending 待重投）：messageId={}, topic={}", messageId, topic);
        }
        CommandVO vo = toVo(command);
        streamService.publish(deviceId, IotStreamService.EVENT_COMMAND, vo);
        return vo;
    }

    /**
     * 指令分页（设备/状态过滤）。
     *
     * @param deviceId 设备ID过滤（可空）
     * @param status   状态过滤（可空）
     * @param current  当前页
     * @param size     每页条数
     * @return 分页结果
     */
    public PageResult<CommandVO> page(Long deviceId, String status, long current, long size) {
        long pageNo = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<IotCommand> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq(IotCommand::getDeviceId, deviceId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(IotCommand::getStatus, status);
        }
        wrapper.orderByDesc(IotCommand::getCreateTime).orderByDesc(IotCommand::getId);
        Page<IotCommand> page = commandMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<CommandVO> records = new ArrayList<>(page.getRecords().size());
        for (IotCommand command : page.getRecords()) {
            records.add(toVo(command));
        }
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    /**
     * 处理设备回执（{@code up/reply} 报文）：
     * {@code {"id":"消息ID","code":0,"data":{...}}}，code=0 记成功、非 0 记失败。
     *
     * @param payload 回执报文
     * @return true 命中指令并处理（含重复回执的幂等忽略）；false 报文不可解析或指令不存在
     */
    public boolean onReply(String payload) {
        JsonNode root = JsonCodec.tree(payload);
        if (root == null || !root.isObject()) {
            return false;
        }
        String messageId = root.path("id").asText("");
        if (!StringUtils.hasText(messageId)) {
            messageId = root.path("messageId").asText("");
        }
        if (!StringUtils.hasText(messageId)) {
            return false;
        }
        IotCommand command = commandMapper.selectOne(new LambdaQueryWrapper<IotCommand>()
                .eq(IotCommand::getMessageId, messageId)
                .last("LIMIT 1"));
        if (command == null) {
            log.warn("回执未命中指令：messageId={}", messageId);
            return false;
        }
        if (STATUS_SUCCESS.equals(command.getStatus()) || STATUS_FAILED.equals(command.getStatus())) {
            return true;
        }
        int code = root.path("code").asInt(0);
        JsonNode data = root.path("data");
        command.setStatus(code == 0 ? STATUS_SUCCESS : STATUS_FAILED);
        command.setReplyJson(JsonCodec.write(data.isMissingNode() || data.isNull()
                ? JsonCodec.toValue(root) : JsonCodec.toValue(data)));
        command.setReplyTime(LocalDateTime.now());
        commandMapper.updateById(command);
        streamService.publish(command.getDeviceId(), IotStreamService.EVENT_COMMAND, toVo(command));
        return true;
    }

    /**
     * 校验指令标识符是否在物模型服务中定义（物模型无服务定义时不限制）。
     *
     * @param productId  产品ID
     * @param identifier 服务标识符
     */
    private void validateIdentifier(Long productId, String identifier) {
        List<IotThingModel> services = thingModelMapper.selectList(new LambdaQueryWrapper<IotThingModel>()
                .eq(IotThingModel::getProductId, productId)
                .eq(IotThingModel::getKind, IotThingModelService.KIND_SERVICE));
        if (services.isEmpty()) {
            return;
        }
        boolean defined = services.stream().anyMatch(model -> identifier.equals(model.getIdentifier()));
        if (!defined) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "iot.command.service-not-defined",
                    "物模型未定义该服务：" + identifier, identifier);
        }
    }

    /**
     * 把指令参数中与属性同名的键写入影子期望值。
     *
     * @param device 设备
     * @param params 指令参数
     */
    private void markDesiredFromParams(IotDevice device, Object params) {
        if (params == null) {
            return;
        }
        List<IotThingModel> properties = thingModelMapper.selectList(new LambdaQueryWrapper<IotThingModel>()
                .eq(IotThingModel::getProductId, device.getProductId())
                .eq(IotThingModel::getKind, IotThingModelService.KIND_PROPERTY));
        if (properties.isEmpty()) {
            return;
        }
        Map<String, Object> desired = new LinkedHashMap<>();
        Map<String, Object> paramMap = JsonCodec.readMap(JsonCodec.write(params));
        for (IotThingModel property : properties) {
            if (paramMap.containsKey(property.getIdentifier())) {
                desired.put(property.getIdentifier(), paramMap.get(property.getIdentifier()));
            }
        }
        shadowService.markDesired(device, desired);
    }

    /**
     * 拼装下行主题。
     *
     * @param device 设备
     * @param suffix 主题后缀（如 {@code /down/command}）
     * @return 主题
     */
    private String topicOf(IotDevice device, String suffix) {
        IotProduct product = productService.requireExists(device.getProductId());
        return TOPIC_PREFIX + device.getTenantId() + "/" + product.getCode()
                + "/" + device.getDeviceName() + suffix;
    }

    /**
     * 下行报文体。
     *
     * @param command 指令实体
     * @return 报文字段（id/identifier/params/ts）
     */
    private static Map<String, Object> downPayload(IotCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", command.getMessageId());
        payload.put("identifier", command.getIdentifier());
        payload.put("params", JsonCodec.tree(command.getParamsJson()));
        payload.put("ts", command.getIssuedTime() == null ? null : command.getIssuedTime().format(TIME_FORMAT));
        return payload;
    }

    /**
     * 实体转视图对象。
     *
     * @param command 指令实体
     * @return 视图对象
     */
    private static CommandVO toVo(IotCommand command) {
        return CommandVO.builder()
                .id(String.valueOf(command.getId()))
                .deviceId(String.valueOf(command.getDeviceId()))
                .identifier(command.getIdentifier())
                .params(JsonCodec.tree(command.getParamsJson()))
                .messageId(command.getMessageId())
                .status(command.getStatus())
                .reply(JsonCodec.tree(command.getReplyJson()))
                .issuedTime(format(command.getIssuedTime()))
                .replyTime(format(command.getReplyTime()))
                .createTime(format(command.getCreateTime()))
                .build();
    }

    /**
     * 格式化时间。
     *
     * @param time 时间
     * @return ISO-8601 文本；空值返回 {@code null}
     */
    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }
}
