package com.example.tools;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class ApiClient {
  static final JsonMapper JSON = JsonMapper.builder().build();
  final String base;
  final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  ApiClient(String base) {
    this.base = base.replaceAll("/+$", "");
  }

  record Reply(int status, String body) {
    JsonNode json() {
      return JSON.readTree(body);
    }
  }

  Reply send(String path, Object data, String token, String method) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("X-Request-Id", "quiz-java-verification");
    if (token != null) b.header("Authorization", "Bearer " + token);
    b.method(
        method,
        data == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(data)));
    var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    return new Reply(r.statusCode(), r.body());
  }

  JsonNode call(String path, Object data, String token, String method, int expected)
      throws Exception {
    var r = send(path, data, token, method);
    check(
        r.status == expected,
        method + " " + path + ": expected " + expected + ", received " + r.status);
    try {
      return r.json();
    } catch (RuntimeException e) {
      return JSON.getNodeFactory().textNode(r.body);
    }
  }

  JsonNode get(String path, String token) throws Exception {
    return call(path, null, token, "GET", 200);
  }

  JsonNode post(String path, Object data, String token, int status) throws Exception {
    return call(path, data, token, "POST", status);
  }

  String login(String email, String password) throws Exception {
    return post("/auth/login", Map.of("email", email, "password", password), null, 200)
        .path("token")
        .asText();
  }

  static String env(String key) {
    String v = System.getenv(key);
    if (v == null || v.isBlank())
      throw new IllegalArgumentException("Set environment variable " + key);
    return v;
  }

  static void check(boolean ok, String reason) {
    if (!ok) throw new IllegalStateException(reason);
  }

  static String id(JsonNode n) {
    return n.path("id").asText();
  }

  static List<JsonNode> items(JsonNode n) {
    List<JsonNode> result = new ArrayList<>();
    n.forEach(result::add);
    return result;
  }
}
