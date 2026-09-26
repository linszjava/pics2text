package com.pics2text.job.infrastructure.messaging;

import com.pics2text.job.messaging.JobRabbitNames;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobRabbitConfiguration {
    @Bean
    Declarables jobRabbitTopology() {
        TopicExchange main = new TopicExchange(JobRabbitNames.MAIN_EXCHANGE, true, false);
        DirectExchange retry = new DirectExchange(JobRabbitNames.RETRY_EXCHANGE, true, false);
        TopicExchange dead = new TopicExchange(JobRabbitNames.DEAD_LETTER_EXCHANGE, true, false);

        Queue analysis = QueueBuilder.durable(JobRabbitNames.ANALYSIS_QUEUE).quorum()
                .deadLetterExchange(JobRabbitNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("analysis").build();
        Queue render = QueueBuilder.durable(JobRabbitNames.RENDER_QUEUE).quorum()
                .deadLetterExchange(JobRabbitNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("render").build();
        Queue analysisRetry = QueueBuilder.durable(JobRabbitNames.ANALYSIS_RETRY_QUEUE).quorum()
                .deadLetterExchange(JobRabbitNames.MAIN_EXCHANGE)
                .deadLetterRoutingKey("analysis").build();
        Queue renderRetry = QueueBuilder.durable(JobRabbitNames.RENDER_RETRY_QUEUE).quorum()
                .deadLetterExchange(JobRabbitNames.MAIN_EXCHANGE)
                .deadLetterRoutingKey("render").build();
        Queue deadQueue = QueueBuilder.durable(JobRabbitNames.DEAD_LETTER_QUEUE).quorum().build();

        return new Declarables(
                main, retry, dead, analysis, render, analysisRetry, renderRetry, deadQueue,
                BindingBuilder.bind(analysis).to(main).with("analysis"),
                BindingBuilder.bind(render).to(main).with("render"),
                BindingBuilder.bind(analysisRetry).to(retry).with("analysis.retry"),
                BindingBuilder.bind(renderRetry).to(retry).with("render.retry"),
                BindingBuilder.bind(deadQueue).to(dead).with("#"));
    }
}
