package com.example.Quiz_Service.exception;

import feign.FeignException;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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

  @ExceptionHandler(FeignException.class)
  public ResponseEntity<Object> handleFeign(FeignException e) {
    log.warn("Question-Service call failed: status={}", e.status());
    if (e.status() == 404) return build(HttpStatus.NOT_FOUND, "Requested questions were not found");
    if (e.status() == 400)
      return build(HttpStatus.BAD_REQUEST, "Invalid request sent to Question-Service");
    return build(
        HttpStatus.SERVICE_UNAVAILABLE, "Question-Service is unavailable, try again later");
  }

  @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
  public ResponseEntity<Object> handleAuthentication(Exception ex) {
    return build(HttpStatus.UNAUTHORIZED, "Invalid credentials");
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  public ResponseEntity<Object> handleDenied(Exception ex) {
    return build(HttpStatus.FORBIDDEN, "Access denied");
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<Object> handleConflict(Exception ex) {
    return build(HttpStatus.CONFLICT, "Resource conflicts with an existing record");
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
