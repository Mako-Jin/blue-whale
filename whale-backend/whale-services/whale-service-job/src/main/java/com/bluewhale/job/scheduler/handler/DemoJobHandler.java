package com.bluewhale.job.scheduler.handler;

import com.bluewhale.job.scheduler.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 内置示例执行器，Bean 名称 {@code demoJobHandler}，用于冒烟与联调。
 *
 * <p>打印并返回一行文本，参数原样回显，便于验证调度引擎的触发、日志落库与结果回写链路。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component("demoJobHandler")
public class DemoJobHandler implements JobHandler {

    @Override
    public String execute(String param) {
        String message = "demoJobHandler executed at " + LocalDateTime.now()
                + (param == null || param.isBlank() ? "" : ", param=" + param);
        log.info(message);
        return message;
    }
}
