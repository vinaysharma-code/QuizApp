package com.example.tools;

import static com.example.tools.ApiClient.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Live API checks that create uniquely named demo data. Run against a test deployment. */
final class SmokeTest {
  final ApiClient api;
  final String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  static final String PASSWORD = "StudentPassword123!";

  SmokeTest(String base) {
    api = new ApiClient(base);
  }

  interface Task<T> {
    T run() throws Exception;
  }

  static <T> T ready(Task<T> task) throws Exception {
    for (int i = 0; ; i++) {
      try {
        return task.run();
      } catch (Exception e) {
        if (i == 89) throw e;
        Thread.sleep(2000);
      }
    }
  }

  static <T> List<T> twice(Callable<T> task) throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    try {
      var a = pool.submit(task);
      var b = pool.submit(task);
      return List.of(a.get(), b.get());
    } finally {
      pool.shutdownNow();
    }
  }

  String student(String name) throws Exception {
    String email = name + "-" + nonce + "@example.test";
    var r =
        api.post(
            "/auth/register",
            Map.of("email", email, "password", PASSWORD, "userName", name, "role", "ADMIN"),
            null,
            201);
    check(
        r.path("role").asText().equals("STUDENT") && !r.has("password"),
        "Signup role/password failure");
    return api.login(email, PASSWORD);
  }

  Map<String, Object> question(String title) {
    return new LinkedHashMap<>(
        Map.of(
            "questionTitle",
            title,
            "optionA",
            "JVM",
            "optionB",
            "CSS",
            "optionC",
            "HTML",
            "optionD",
            "SQL",
            "rightAnswer",
            "JVM",
            "category",
            "smoke-" + nonce,
            "difficultyLevel",
            "easy"));
  }

  JsonNode start(String quiz, String token, int status) throws Exception {
    return api.post("/quiz/" + quiz + "/attempts", Map.of(), token, status);
  }

  JsonNode submit(JsonNode attempt, Object answers, String token, int status) throws Exception {
    return api.post("/attempts/" + id(attempt) + "/submit", answers, token, status);
  }

  static List<String> ids(JsonNode n) {
    return items(n).stream().map(ApiClient::id).toList();
  }

  static List<Map<String, String>> answers(JsonNode a, String value) {
    return items(a.path("questions")).stream()
        .map(q -> Map.of("id", id(q), "response", value))
        .toList();
  }

  String draft(Map<String, Object> body, String token) throws Exception {
    return id(api.post("/admin/quizzes", body, token, 201).path("quiz"));
  }

  void publish(String id, String token) throws Exception {
    api.post("/admin/quizzes/" + id + "/publish", Map.of(), token, 200);
  }

  static String wrong(JsonNode q) {
    for (String key : List.of("optionA", "optionB", "optionC", "optionD"))
      if (!q.path(key).asText().equals(q.path("rightAnswer").asText())) return q.path(key).asText();
    throw new IllegalStateException("No incorrect option");
  }

  void run() throws Exception {
    String admin = ready(() -> api.login(env("DEMO_ADMIN_EMAIL"), env("DEMO_ADMIN_PASSWORD")));
    ready(() -> api.get("/quiz/published", admin));
    ready(() -> api.get("/question/getAllQuestion", admin));
    api.post(
        "/auth/login",
        Map.of("email", env("DEMO_ADMIN_EMAIL"), "password", "wrong-password"),
        null,
        401);
    String s1 = student("student"), s2 = student("other");
    api.call("/question/getAllQuestion", null, null, "GET", 401);
    for (String path :
        List.of(
            "/question/getAllQuestion",
            "/question/getByCategory/java",
            "/question/snapshot?category=java&numQuestion=1")) api.call(path, null, s1, "GET", 403);
    api.post("/question/getScore", List.of(Map.of("id", "any", "response", "JVM")), s1, 403);
    var legacy =
        Map.of(
            "categoryName",
            "smoke-" + nonce,
            "noQ",
            2,
            "title",
            "Snapshot demo",
            "durationSeconds",
            600);
    api.post("/quiz/create", legacy, s1, 403);
    api.call("/quiz/published", null, "invalid", "GET", 401);
    String[] jwt = s1.split("\\.");
    ObjectNode claims = (ObjectNode) JSON.readTree(Base64.getUrlDecoder().decode(jwt[1]));
    claims.put("role", "ADMIN");
    jwt[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(claims));
    api.call("/question/getAllQuestion", null, String.join(".", jwt), "GET", 401);
    for (String title : List.of("Original runtime question", "Original machine question"))
      api.post("/question/addQuestions", question(title), admin, 201);
    JsonNode created = null;
    // A known 503 occurs before publication is saved. Do not retry ambiguous write failures.
    for (int i = 0; i < 90; i++) {
      var r = api.send("/quiz/create", legacy, admin, "POST");
      if (r.status() == 201) {
        try {
          created = r.json();
        } catch (RuntimeException ex) {
          // The legacy endpoint returns a plain-text UUID, unlike the JSON draft API.
          created = JSON.getNodeFactory().textNode(r.body());
        }
        break;
      }
      check(r.status() == 503, "Unexpected publication response: " + r.status());
      Thread.sleep(2000);
    }
    check(created != null, "Publication discovery failed");
    String quiz = created.asText();
    var bankRows = api.get("/question/getByCategory/smoke-" + nonce, admin);
    var edited = question("EDITED bank question");
    edited.put("rightAnswer", "CSS");
    api.call("/question/" + id(bankRows.get(0)), edited, admin, "PUT", 200);
    JsonNode attempt = start(quiz, s1, 201);
    check(
        !attempt.toString().contains("rightAnswer")
            && items(attempt.path("questions")).stream()
                .noneMatch(q -> q.path("questionTitle").asText().startsWith("EDITED")),
        "Snapshot changed or key exposed");
    check(
        attempt.path("score").isNull() && attempt.path("status").asText().equals("IN_PROGRESS"),
        "Invalid initial attempt");
    api.call("/attempts/" + id(attempt), null, s2, "GET", 403);
    submit(attempt, List.of(Map.of("id", "foreign", "response", "JVM")), s1, 400);
    check(
        api.get("/attempts/" + id(attempt), s1).path("status").asText().equals("IN_PROGRESS"),
        "Invalid answer finalized attempt");
    var correctOriginal = answers(attempt, "JVM");
    submit(attempt, List.of(correctOriginal.get(0), correctOriginal.get(0)), s1, 400);
    var results = twice(() -> submit(attempt, correctOriginal, s1, 200));
    check(
        results.get(0).equals(results.get(1))
            && results.get(0).path("score").asInt() == 2
            && results.get(0).path("status").asText().equals("SUBMITTED"),
        "Concurrent submissions disagree");
    check(
        submit(attempt, answers(attempt, "CSS"), s1, 200).equals(results.get(0)),
        "Replay changed result");
    var history = api.get("/attempts", s1);
    check(
        ids(history.path("content")).contains(id(attempt))
            && api.get("/attempts", s2).path("totalElements").asInt() == 0,
        "History ownership failure");
    check(
        !history.toString().contains("rightAnswer") && !history.toString().contains("password"),
        "Private fields exposed");
    String directQuestion = System.getenv("DIRECT_QUESTION_URL"),
        directQuiz = System.getenv("DIRECT_QUIZ_URL");
    if (directQuestion != null) {
      var direct = new ApiClient(directQuestion);
      direct.call("/question/getAllQuestion", null, s1, "GET", 403);
      direct.call("/question/getAllQuestion", null, null, "GET", 401);
    }
    if (directQuiz != null) new ApiClient(directQuiz).post("/quiz/create", legacy, s1, 403);
    System.out.println("PASS JWT, roles, snapshot stability, concurrent submission and replay");
    var latest = api.get("/question/getByCategory/smoke-" + nonce, admin);
    Map<String, JsonNode> bank = new HashMap<>();
    latest.forEach(q -> bank.put(id(q), q));
    List<String> selected = List.of(id(latest.get(1)), id(latest.get(0)));
    Map<String, Object> request =
        new LinkedHashMap<>(
            Map.of(
                "title",
                "Manual assessment " + nonce,
                "questionIds",
                selected,
                "durationSeconds",
                600,
                "maxAttempts",
                1,
                "passingPercent",
                100,
                "resultReleasePolicy",
                "MANUAL"));
    String manual = draft(request, admin), path = "/admin/quizzes/" + manual;
    check(
        !ids(api.get("/quiz/published", s1).path("content")).contains(manual),
        "Draft in student catalog");
    start(manual, s1, 404);
    var preview = api.get(path + "/preview", admin);
    check(
        ids(preview.path("previewQuestions")).equals(selected)
            && !preview.toString().contains("rightAnswer"),
        "Preview selection/key failure");
    request.put("title", "Edited assessment " + nonce);
    api.call(path, request, admin, "PUT", 200);
    publish(manual, admin);
    api.call(path, request, admin, "PUT", 409);
    api.call(path + "/attempts", null, s1, "GET", 403);
    if (System.getenv("DEMO_OTHER_ADMIN_EMAIL") != null) {
      String other = api.login(env("DEMO_OTHER_ADMIN_EMAIL"), env("DEMO_OTHER_ADMIN_PASSWORD"));
      for (String suffix : List.of("", "/preview", "/attempts", "/statistics", "/results.csv"))
        api.call(path + suffix, null, other, "GET", 403);
      for (String suffix : List.of("/publish", "/release-results"))
        api.post(path + suffix, Map.of(), other, 403);
      api.call(path, request, other, "PUT", 403);
      String second = draft(request, other);
      api.call("/admin/quizzes/" + second, null, admin, "GET", 403);
      check(
          !ids(api.get("/admin/quizzes", other).path("content")).contains(manual),
          "Owner listing leak");
      if (directQuiz != null)
        new ApiClient(directQuiz).call(path + "/attempts", null, other, "GET", 403);
      System.out.println("PASS second-admin isolation");
    } else System.out.println("SKIP second-admin checks: set DEMO_OTHER_ADMIN_EMAIL/PASSWORD");
    var starts = twice(() -> api.send("/quiz/" + manual + "/attempts", Map.of(), s1, "POST"));
    check(
        starts.stream().filter(r -> r.status() == 201).count() == 1
            && starts.stream().filter(r -> r.status() == 200).count() == 1,
        "Concurrent start resume failure");
    var ma = starts.stream().filter(r -> r.status() == 201).findFirst().orElseThrow().json();
    check(
        ids(ma.path("questions")).equals(selected) && ma.path("attemptNumber").asInt() == 1,
        "Attempt selection/number failure");
    var correct =
        selected.stream()
            .map(i -> Map.of("id", i, "response", bank.get(i).path("rightAnswer").asText()))
            .toList();
    var withheld = submit(ma, correct, s1, 200);
    check(
        withheld.path("score").isNull()
            && withheld.path("passed").isNull()
            && !withheld.path("resultReleased").asBoolean(),
        "Premature release");
    check(
        api.get("/attempts/" + id(ma), s1).path("score").isNull(), "Detail reveals withheld score");
    check(
        items(api.get("/attempts", s1).path("content")).stream()
            .filter(a -> id(a).equals(id(ma)))
            .findFirst()
            .orElseThrow()
            .path("score")
            .isNull(),
        "History reveals withheld score");
    start(manual, s1, 409);
    var m2 = start(manual, s2, 201);
    submit(
        m2,
        List.of(
            Map.of("id", selected.get(0), "response", wrong(bank.get(selected.get(0)))),
            correct.get(1)),
        s2,
        200);
    String formulaEmail = "formula-" + nonce + "@example.test";
    api.post(
        "/auth/register",
        Map.of("email", formulaEmail, "password", PASSWORD, "userName", "=2+2"),
        null,
        201);
    String s3 = api.login(formulaEmail, PASSWORD);
    var m3 = start(manual, s3, 201);
    submit(
        m3,
        selected.stream().map(i -> Map.of("id", i, "response", wrong(bank.get(i)))).toList(),
        s3,
        200);
    var report = api.get(path + "/attempts", admin);
    check(
        report.path("totalElements").asInt() == 3
            && items(report.path("content")).stream()
                .map(a -> a.path("score").asInt())
                .sorted()
                .toList()
                .equals(List.of(0, 1, 2)),
        "Report score/count failure");
    check(
        items(report.path("content")).stream()
            .allMatch(
                a ->
                    !a.path("studentName").asText().isBlank()
                        && !a.path("studentEmail").asText().isBlank()
                        && !a.path("timeTakenSeconds").isNull()),
        "Missing participant information");
    var stats = api.get(path + "/statistics", admin);
    check(
        stats.path("uniqueParticipants").asInt() == 3
            && stats.path("totalAttempts").asInt() == 3
            && stats.path("submittedAttempts").asInt() == 3
            && stats.path("passedAttempts").asInt() == 1
            && stats.path("averageScore").asDouble() == 1
            && stats.path("highestScore").asInt() == 2,
        "Statistics mismatch");
    check(
        api.get(path + "/attempts?passed=true", admin).path("totalElements").asInt() == 1,
        "Passed filter failure");
    check(
        api.get(path + "/attempts?passed=false", admin).path("totalElements").asInt() == 2,
        "Failed filter failure");
    check(
        api.get(path + "/attempts?search=" + formulaEmail, admin).path("totalElements").asInt()
            == 1,
        "Search failure");
    check(
        api.get(path + "/attempts?page=1&size=2", admin).path("numberOfElements").asInt() == 1,
        "Pagination failure");
    api.call(path + "/attempts?status=UNKNOWN", null, admin, "GET", 400);
    api.call(path + "/attempts?size=101", null, admin, "GET", 400);
    Instant now = Instant.now();
    check(
        api.get(
                    path
                        + "/attempts?from="
                        + encode(now.minus(1, ChronoUnit.DAYS))
                        + "&to="
                        + encode(now.plus(1, ChronoUnit.DAYS)),
                    admin)
                .path("totalElements")
                .asInt()
            == 3,
        "Date filter failure");
    var csvReply = api.send(path + "/results.csv?status=SUBMITTED", null, admin, "GET");
    check(csvReply.status() == 200, "CSV response failure");
    var csv = parseCsv(csvReply.body());
    check(csv.size() == 4, "CSV row count failure");
    int emailColumn = csv.get(0).indexOf("student_email"),
        nameColumn = csv.get(0).indexOf("student_name");
    check(emailColumn >= 0 && nameColumn >= 0, "Missing CSV columns");
    check(
        csv.stream()
            .skip(1)
            .filter(row -> row.get(emailColumn).equals(formulaEmail))
            .findFirst()
            .orElseThrow()
            .get(nameColumn)
            .equals("'=2+2"),
        "CSV formula injection");
    api.post(path + "/release-results", Map.of(), admin, 200);
    var released = api.get("/attempts/" + id(ma), s1);
    check(
        released.path("score").asInt() == 2
            && released.path("passed").asBoolean()
            && released.path("resultReleased").asBoolean(),
        "Manual release failure");
    check(!api.get("/attempts/" + id(m2), s2).path("passed").asBoolean(), "Pass threshold failure");
    start(quiz, s1, 201);
    var retakes = api.get("/admin/quizzes/" + quiz + "/statistics", admin);
    check(
        retakes.path("uniqueParticipants").asInt() == 1
            && retakes.path("totalAttempts").asInt() == 2,
        "Participants conflated with attempts");
    Map<String, Object> future = new LinkedHashMap<>(request);
    now = Instant.now();
    future.put("opensAt", now.plusSeconds(90).toString());
    future.put("closesAt", now.plusSeconds(180).toString());
    future.put("resultReleasePolicy", "IMMEDIATE");
    String futureId = draft(future, admin);
    publish(futureId, admin);
    start(futureId, s1, 409);
    System.out.println("PASS drafts, reports, CSV, manual release and concurrent start limits");
    if ("1".equals(System.getenv("RUN_EXPIRY_TEST"))) {
      now = Instant.now().truncatedTo(ChronoUnit.MICROS);
      Instant close = now.plusSeconds(30);
      Map<String, Object> closing = new LinkedHashMap<>(request);
      closing.put("opensAt", now.minusSeconds(10).toString());
      closing.put("closesAt", close.toString());
      closing.put("resultReleasePolicy", "AFTER_CLOSE");
      String closingId = draft(closing, admin);
      publish(closingId, admin);
      var c1 = start(closingId, s1, 201);
      var c2 = start(closingId, s2, 201);
      check(
          Instant.parse(c1.path("deadline").asText()).equals(close),
          "Closing time did not cap deadline");
      check(submit(c1, correct, s1, 200).path("score").isNull(), "AFTER_CLOSE released early");
      System.out.println("Checking real closing deadline and AFTER_CLOSE release (31 seconds)...");
      Thread.sleep(31000);
      check(
          api.get("/attempts/" + id(c1), s1).path("score").asInt() == 2,
          "AFTER_CLOSE did not release");
      var expired = submit(c2, correct, s2, 410);
      check(
          expired.path("status").asText().equals("EXPIRED") && expired.path("score").isNull(),
          "Late submission accepted");
      start(closingId, s3, 410);
    } else System.out.println("SKIP real deadline test: set RUN_EXPIRY_TEST=1");
    System.out.println("PASS Java live API suite");
    System.out.println(
        JSON.writeValueAsString(
            Map.of(
                "quizId",
                quiz,
                "attemptId",
                id(attempt),
                "manualQuizId",
                manual,
                "manualAttemptId",
                id(ma))));
  }

  static String encode(Instant i) {
    return URLEncoder.encode(i.toString(), StandardCharsets.UTF_8);
  }

  static List<List<String>> parseCsv(String input) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '"') {
        if (quoted && i + 1 < input.length() && input.charAt(i + 1) == '"') {
          cell.append('"');
          i++;
        } else quoted = !quoted;
      } else if (c == ',' && !quoted) {
        row.add(cell.toString());
        cell.setLength(0);
      } else if ((c == '\r' || c == '\n') && !quoted) {
        if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
        row.add(cell.toString());
        rows.add(row);
        row = new ArrayList<>();
        cell.setLength(0);
      } else cell.append(c);
    }
    check(!quoted, "Unclosed CSV quote");
    if (cell.length() > 0 || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    return rows;
  }
}
