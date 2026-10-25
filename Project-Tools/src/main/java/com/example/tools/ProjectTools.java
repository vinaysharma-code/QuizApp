package com.example.tools;

import java.util.Arrays;

public class ProjectTools {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.out.println(
          "Usage: java -jar Project-Tools/target/project-tools.jar smoke [gateway-url]\n"
              + "       java -jar Project-Tools/target/project-tools.jar load [--url URL --path"
              + " PATH --requests N --concurrency N --output FILE]");
      return;
    }
    switch (args[0]) {
      case "smoke" -> new SmokeTest(args.length > 1 ? args[1] : "http://localhost:8765").run();
      case "load" -> LoadTest.run(Arrays.copyOfRange(args, 1, args.length));
      default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
    }
  }
}
