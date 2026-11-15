package com.seashare.seasharem3.infrastructure.adapter.in.web;

import com.seashare.seasharem3.application.dto.BatchEstimateResult;
import com.seashare.seasharem3.application.dto.EstimateBatchCommand;
import com.seashare.seasharem3.application.dto.EstimateSingleCommand;
import com.seashare.seasharem3.application.dto.SingleEstimateResult;
import com.seashare.seasharem3.application.port.in.EstimateBatchUseCase;
import com.seashare.seasharem3.application.port.in.EstimateSingleUseCase;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.BatchEstimateRequest;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.BatchEstimateResponse;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.SingleEstimateRequest;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.SingleEstimateResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/estimates")
public class EstimateController {

    private final EstimateBatchUseCase batchUseCase;
    private final EstimateSingleUseCase singleUseCase;

    public EstimateController(EstimateBatchUseCase batchUseCase,
                              EstimateSingleUseCase singleUseCase) {
        this.batchUseCase = batchUseCase;
        this.singleUseCase = singleUseCase;
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchEstimateResponse> estimateBatch(
            @Valid @RequestBody BatchEstimateRequest request) {
        BatchEstimateResult result = batchUseCase.estimate(
                new EstimateBatchCommand(request.boatIds()));
        return ResponseEntity.ok(toBatchResponse(result));
    }

    @PostMapping("/individual")
    public ResponseEntity<SingleEstimateResponse> estimateIndividual(
            @Valid @RequestBody SingleEstimateRequest request) {
        SingleEstimateResult result = singleUseCase.estimate(
                new EstimateSingleCommand(
                        request.boatId(),
                        request.startDate(),
                        request.endDate(),
                        request.passengers()));
        return ResponseEntity.ok(toSingleResponse(result));
    }

    private static BatchEstimateResponse toBatchResponse(BatchEstimateResult r) {
        return new BatchEstimateResponse(
                r.evaluationDate(),
                r.durationDays(),
                r.passengers(),
                r.estimates().stream()
                        .map(e -> new BatchEstimateResponse.EstimateItem(e.boatId(), e.estimatedTotal()))
                        .toList(),
                r.unavailable().stream()
                        .map(u -> new BatchEstimateResponse.UnavailableItem(u.boatId(), u.reason()))
                        .toList());
    }

    private static SingleEstimateResponse toSingleResponse(SingleEstimateResult r) {
        return new SingleEstimateResponse(
                r.boatId(),
                r.startDate(),
                r.endDate(),
                r.durationDays(),
                r.passengers(),
                r.estimatedTotal(),
                r.warning());
    }
}
