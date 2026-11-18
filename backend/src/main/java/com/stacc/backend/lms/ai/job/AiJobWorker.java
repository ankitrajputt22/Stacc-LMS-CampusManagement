package com.stacc.backend.lms.ai.job;

import com.stacc.backend.lms.ai.AiServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "stacc.ai", name = "enabled", havingValue = "true")
public class AiJobWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiJobWorker.class);

    private final AiJobService jobs;
    private final AiServiceClient client;

    public AiJobWorker(AiJobService jobs, AiServiceClient client) {
        this.jobs = jobs;
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int failed = jobs.recoverAfterRestart();
        if (failed > 0) {
            LOGGER.info("{} AI job(s) left running by a restart were marked failed.", failed);
        }
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void tick() {
        if (client.isEnabled()) {
            jobs.runNext();
        }
    }
}
