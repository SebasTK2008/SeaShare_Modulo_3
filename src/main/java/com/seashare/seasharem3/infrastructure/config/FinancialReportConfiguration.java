package com.seashare.seasharem3.infrastructure.config;

import com.seashare.seasharem3.application.port.out.ClockPort;
import com.seashare.seasharem3.application.port.out.FinancialReportQueryPort;
import com.seashare.seasharem3.application.service.FinancialReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class FinancialReportConfiguration {

    @Bean
    public FinancialReportService financialReportService(FinancialReportQueryPort queryPort,
                                                         ClockPort clockPort, Clock instantClock) {
        return new FinancialReportService(queryPort, clockPort, instantClock);
    }
}
