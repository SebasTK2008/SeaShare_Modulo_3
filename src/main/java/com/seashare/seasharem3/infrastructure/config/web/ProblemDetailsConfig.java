package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.infrastructure.adapter.in.web.error.ApiProblemDetailsFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Expone el constructor de Problem Details como bean. [CONV] núcleo compartido. */
@Configuration
public class ProblemDetailsConfig {

    @Bean
    public ApiProblemDetailsFactory apiProblemDetailsFactory() {
        return new ApiProblemDetailsFactory();
    }
}
