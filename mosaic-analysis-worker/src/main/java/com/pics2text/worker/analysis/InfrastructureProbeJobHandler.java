package com.pics2text.worker.analysis;

import com.pics2text.job.application.JobExecutionContext;
import com.pics2text.job.application.JobHandler;
import com.pics2text.job.domain.JobType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
public class InfrastructureProbeJobHandler implements JobHandler {
    private final ObjectMapper objectMapper;

    public InfrastructureProbeJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(JobType type) {
        return type == JobType.INFRASTRUCTURE_PROBE;
    }

    @Override
    public String execute(JobExecutionContext context) throws Exception {
        JsonNode payload = objectMapper.readTree(context.payloadJson());
        int failUntilAttempt = payload.path("failUntilAttempt").asInt(0);
        long durationMillis = payload.path("durationMillis").asLong(0);
        long elapsed = 0;
        while (elapsed < durationMillis) {
            if (context.cancellationRequested()) {
                return objectMapper.writeValueAsString(Map.of("cancelledAtCheckpoint", true));
            }
            long step = Math.min(500, durationMillis - elapsed);
            Thread.sleep(step);
            elapsed += step;
            int progress = durationMillis == 0 ? 90 : (int) Math.min(90, elapsed * 90 / durationMillis);
            context.heartbeat(progress);
        }
        if (context.attempt() <= failUntilAttempt) {
            throw new IllegalStateException("探针按配置触发第 " + context.attempt() + " 次失败");
        }
        return objectMapper.writeValueAsString(Map.of(
                "probe", "ok",
                "attempt", context.attempt()));
    }
}
