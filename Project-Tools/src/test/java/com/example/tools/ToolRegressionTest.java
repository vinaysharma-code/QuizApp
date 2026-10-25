package com.example.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolRegressionTest {
  @Test
  void csvPreservesQuotedNamesAndNewlines() {
    assertEquals(
        List.of(List.of("name", "email"), List.of("Doe, \"Vinay\"\nSharma", "v@example.test")),
        SmokeTest.parseCsv("name,email\r\n\"Doe, \"\"Vinay\"\"\nSharma\",\"v@example.test\"\r\n"));
  }

  @Test
  void malformedCsvFails() {
    assertThrows(IllegalStateException.class, () -> SmokeTest.parseCsv("name\n\"unfinished"));
  }

  @Test
  void checksWorkWithoutAssertFlag() {
    assertThrows(IllegalStateException.class, () -> ApiClient.check(false, "Failed expectation"));
  }

  @Test
  void errorsOmitResponseSecrets() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/failure",
        exchange -> {
          byte[] b = "private-token-example".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(403, b.length);
          try (var out = exchange.getResponseBody()) {
            out.write(b);
          }
        });
    server.start();
    try {
      var api = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
      var ex = assertThrows(IllegalStateException.class, () -> api.get("/failure", null));
      assertTrue(ex.getMessage().contains("403"));
      assertFalse(ex.getMessage().contains("private-token"));
    } finally {
      server.stop(0);
    }
  }
}
