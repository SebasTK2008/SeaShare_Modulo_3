package com.seashare.seasharem3.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EstimateConfiguration {

    @Value("${seashare.estimates.max-batch-size:50}")
    private int maxBatchSize;

    public int maxBatchSize() {
        return maxBatchSize;
    }
}
