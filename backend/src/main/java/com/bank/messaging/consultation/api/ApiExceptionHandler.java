package com.bank.messaging.consultation.api;

import com.bank.messaging.consultation.application.InvalidMessageQueryException;
import com.bank.messaging.consultation.application.MessageNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(InvalidMessageQueryException.class)
    ProblemDetail invalidQuery(InvalidMessageQueryException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(MessageNotFoundException.class)
    ProblemDetail messageNotFound(MessageNotFoundException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ProblemDetail databaseUnavailable(Exception error) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "La consultation est temporairement indisponible.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception error,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            body = ProblemDetail.forStatusAndDetail(status, "Paramètres de requête invalides.");
        }
        return super.handleExceptionInternal(error, body, headers, status, request);
    }
}
