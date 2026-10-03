package com.pics2text.job.infrastructure.messaging;

import com.pics2text.job.domain.JobType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class JobWorkerMetrics {
    private final MeterRegistry registry;

    public JobWorkerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void received(JobType type) {
        counter("pics2text.jobs.messages", "result", "received", type).increment();
    }

    public void malformed() {
        Counter.builder("pics2text.jobs.messages")
                .description("作业消息消费结果")
                .tag("result", "malformed")
                .tag("type", "unknown")
                .register(registry)
                .increment();
    }

    public void skipped(JobType type, String reason) {
        Counter.builder("pics2text.jobs.executions")
                .description("作业执行结果")
                .tag("result", "skipped")
                .tag("reason", reason)
                .tag("type", type.name())
                .register(registry)
                .increment();
    }

    public void completed(JobType type, String result, long startedNanos) {
        Counter.builder("pics2text.jobs.executions")
                .description("作业执行结果")
                .tag("result", result)
                .tag("reason", "none")
                .tag("type", type.name())
                .register(registry)
                .increment();
        Timer.builder("pics2text.jobs.duration")
                .description("Worker 作业执行耗时")
                .tag("result", result)
                .tag("type", type.name())
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofNanos(System.nanoTime() - startedNanos));
    }

    public void infrastructureFailure(JobType type) {
        counter("pics2text.jobs.messages", "result", "requeued", type).increment();
    }

    private Counter counter(String name, String tagName, String tagValue, JobType type) {
        return Counter.builder(name)
                .description("作业消息消费结果")
                .tag(tagName, tagValue)
                .tag("type", type.name())
                .register(registry);
    }
}
