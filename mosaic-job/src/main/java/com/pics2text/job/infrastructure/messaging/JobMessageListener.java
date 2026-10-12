package com.pics2text.job.infrastructure.messaging;

import com.pics2text.job.application.JobCoordinator;
import com.pics2text.job.application.JobHandler;
import com.pics2text.job.application.PermanentJobException;
import com.pics2text.job.messaging.JobMessage;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@ConditionalOnProperty(name = "jobs.worker.enabled", havingValue = "true")
public class JobMessageListener {
    private static final Logger log = LoggerFactory.getLogger(JobMessageListener.class);

    private final JobCoordinator coordinator;
    private final List<JobHandler> handlers;
    private final ObjectMapper objectMapper;
    private final String workerId;
    private final String consumerName;

    public JobMessageListener(JobCoordinator coordinator, List<JobHandler> handlers, ObjectMapper objectMapper,
            org.springframework.core.env.Environment environment) {
        this.coordinator = coordinator;
        this.handlers = handlers;
        this.objectMapper = objectMapper;
        this.workerId = environment.getProperty("jobs.worker.id",
                environment.getProperty("spring.application.name", "worker") + "-" + ProcessHandle.current().pid());
        this.consumerName = environment.getProperty("jobs.worker.consumer-name",
                environment.getProperty("spring.application.name", "worker"));
    }

    @RabbitListener(queues = "${jobs.worker.queue}")
    public void receive(Message raw, Channel channel) throws Exception {
        long tag = raw.getMessageProperties().getDeliveryTag();
        JobMessage message;
        try {
            message = objectMapper.readValue(new String(raw.getBody(), StandardCharsets.UTF_8), JobMessage.class);
        } catch (Exception exception) {
            log.error("无法解析作业消息，将进入死信队列", exception);
            channel.basicReject(tag, false);
            return;
        }

        try (MDC.MDCCloseable ignoredJob = MDC.putCloseable("jobId", message.jobId().toString());
             MDC.MDCCloseable ignoredProject = MDC.putCloseable("projectId", message.projectId().toString());
             MDC.MDCCloseable ignoredEvent = MDC.putCloseable("eventId", message.eventId().toString())) {
            JobCoordinator.BeginResult begin = coordinator.begin(message, consumerName, workerId);
            if (begin.status() != JobCoordinator.BeginStatus.STARTED) {
                channel.basicAck(tag, false);
                return;
            }
            try {
                JobHandler handler = handlers.stream().filter(candidate -> candidate.supports(message.type()))
                        .findFirst().orElseThrow(() -> new PermanentJobException("没有可处理该类型的处理器：" + message.type()));
                String result = handler.execute(begin.context());
                if (begin.context().cancellationRequested()) {
                    coordinator.cancel(message, consumerName, workerId);
                } else {
                    coordinator.succeed(message, consumerName, workerId, result);
                }
            } catch (Throwable failure) {
                coordinator.fail(message, consumerName, workerId, failure);
            }
            channel.basicAck(tag, false);
        } catch (Exception infrastructureFailure) {
            log.error("处理作业消息时基础设施异常，消息重新入队 jobId={}", message.jobId(), infrastructureFailure);
            channel.basicNack(tag, false, true);
        }
    }

}
