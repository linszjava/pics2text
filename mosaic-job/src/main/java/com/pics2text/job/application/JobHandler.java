package com.pics2text.job.application;

import com.pics2text.job.domain.JobType;

public interface JobHandler {
    boolean supports(JobType type);

    String execute(JobExecutionContext context) throws Exception;
}
