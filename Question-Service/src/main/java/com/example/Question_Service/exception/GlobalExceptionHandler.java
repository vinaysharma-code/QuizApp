package com.example.Question_Service.exception;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String msg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + " " + f.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return build(HttpStatus.BAD_REQUEST, msg);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String msg =
        (ex instanceof ErrorResponse er && er.getBody().getDetail() != null)
            ? er.getBody().getDetail()
            : "Request failed";
    return build(status, msg);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Object> handleDuplicate(DataIntegrityViolationException e) {
    return build(
        HttpStatus.CONFLICT,
        "Question conflicts with a database constraint (for example, duplicate category and"
            + " title)");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleOther(Exception e) {
    log.error("Unexpected error", e);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
  }

  private ResponseEntity<Object> build(HttpStatusCode s, String msg) {
    return ResponseEntity.status(s)
        .body(
            Map.of(
                "timestamp", Instant.now().toString(),
                "status", s.value(),
                "message", msg));
  }
}
