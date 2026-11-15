package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.out.FinancialRecordQueryPort;
import com.seashare.seasharem3.application.service.FinancialRecordsQueryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FinancialRecordsConfiguration {

    @Bean
    public FinancialRecordsQueryService financialRecordsQueryService(FinancialRecordQueryPort queryPort) {
        return new FinancialRecordsQueryService(queryPort);
    }
}
