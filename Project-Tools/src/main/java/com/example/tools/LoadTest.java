package com.example.tools;

import static com.example.tools.ApiClient.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

final class LoadTest {
  record Sample(double millis, String status) {}

  static void run(String[] args) throws Exception {
    Map<String, String> opts =
        new HashMap<>(
            Map.of(
                "--url",
                "http://localhost:8765",
                "--path",
                "/quiz/published",
                "--requests",
                "200",
                "--concurrency",
                "10"));
    for (int i = 0; i < args.length; i += 2) {
      check(
          i + 1 < args.length
              && Set.of("--url", "--path", "--requests", "--concurrency", "--output")
                  .contains(args[i]),
          "Unknown or incomplete option: " + args[i]);
      opts.put(args[i], args[i + 1]);
    }
    int count = Integer.parseInt(opts.get("--requests")),
        concurrency = Integer.parseInt(opts.get("--concurrency"));
    check(
        count >= 1 && count <= 100000 && concurrency >= 1 && concurrency <= 100,
        "requests must be 1..100000; concurrency must be 1..100");
    String path = opts.get("--path");
    check(path.startsWith("/") && !path.startsWith("//"), "path must begin with one slash");
    ApiClient api = new ApiClient(opts.get("--url"));
    String supplied = System.getenv("LOAD_TEST_TOKEN");
    String token =
        supplied == null || supplied.isBlank()
            ? api.login(env("DEMO_EMAIL"), env("DEMO_PASSWORD"))
            : supplied;
    for (int i = 0; i < 3; i++)
      check(sample(api, path, token).status.equals("200"), "Warm-up failed");
    var pool = Executors.newFixedThreadPool(concurrency);
    List<Sample> results = new ArrayList<>();
    long start = System.nanoTime();
    try {
      List<Callable<Sample>> tasks = new ArrayList<>();
      for (int i = 0; i < count; i++) tasks.add(() -> sample(api, path, token));
      for (var f : pool.invokeAll(tasks)) results.add(f.get());
    } finally {
      pool.shutdownNow();
    }
    double elapsed = (System.nanoTime() - start) / 1e9;
    double[] sorted = results.stream().mapToDouble(Sample::millis).sorted().toArray();
    Map<String, Long> statuses = new TreeMap<>();
    results.forEach(s -> statuses.merge(s.status, 1L, Long::sum));
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("requests", count);
    report.put("concurrency", concurrency);
    report.put("path", path);
    report.put("elapsed_seconds", Math.round(elapsed * 1000) / 1000.0);
    report.put("requests_per_second", round(count / elapsed));
    report.put(
        "latency_ms",
        Map.of(
            "mean",
            round(Arrays.stream(sorted).average().orElseThrow()),
            "p50",
            percentile(sorted, .5),
            "p95",
            percentile(sorted, .95),
            "p99",
            percentile(sorted, .99)));
    report.put("status_counts", statuses);
    String text = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report);
    System.out.println(text);
    if (opts.containsKey("--output"))
      Files.writeString(Path.of(opts.get("--output")), text + System.lineSeparator());
    check(statuses.keySet().equals(Set.of("200")), "Load check included unsuccessful responses");
  }

  static Sample sample(ApiClient api, String path, String token) {
    long start = System.nanoTime();
    String status;
    try {
      status = String.valueOf(api.send(path, null, token, "GET").status());
    } catch (Exception ex) {
      status = "network_error";
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
    }
    return new Sample((System.nanoTime() - start) / 1e6, status);
  }

  static double round(double n) {
    return Math.round(n * 100) / 100.0;
  }

  static double percentile(double[] sorted, double p) {
    return round(sorted[(int) ((sorted.length - 1) * p)]);
  }
}
