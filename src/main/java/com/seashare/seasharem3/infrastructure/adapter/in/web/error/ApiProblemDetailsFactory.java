package com.seashare.seasharem3.infrastructure.adapter.in.web.error;

import com.seashare.seasharem3.application.exception.ApiFieldError;
import com.seashare.seasharem3.application.exception.ErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;

/**
 * Constructor de Problem Details (RFC 9457) conforme a contracts/README §3.3: campos
 * {@code type=about:blank}, {@code title}, {@code status}, {@code detail}, {@code code},
 * {@code retryable} y {@code errors} opcional (solo validación).
 * [CONV] núcleo compartido.
 */
public class ApiProblemDetailsFactory {

    private static final URI TYPE_ABOUT_BLANK = URI.create("about:blank");

    public ProblemDetail create(ErrorCode code, String detail) {
        return create(code, detail, List.of());
    }

    public ProblemDetail create(ErrorCode code, String detail, List<ApiFieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.httpStatus()), detail);
        problem.setType(TYPE_ABOUT_BLANK);
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        problem.setProperty("retryable", code.retryable());
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}
