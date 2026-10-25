import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.*;

/** Compose PostgreSQL backups and non-destructive restore drills; requires JDK 17 and Docker. */
class DatabaseBackup {
  static final Map<String, String> DATABASES =
      Map.of("question-db", "questions", "quiz-db", "quizzes");

  public static void main(String[] args) throws Exception {
    if (args.length != 2 || !Set.of("backup", "verify").contains(args[0]))
      throw new IllegalArgumentException(
          "Run from project root: java scripts/DatabaseBackup.java backup|verify DIRECTORY");
    if (!Files.isRegularFile(Path.of("compose.yaml")))
      throw new IllegalStateException("Run from project root");
    Path dir = Path.of(args[1]);
    if (args[0].equals("backup")) {
      Files.createDirectories(dir);
      String stamp = Instant.now().toString().replace(':', '-');
      Path destination = dir.resolve(stamp);
      Files.createDirectory(destination);
      for (var entry : DATABASES.entrySet()) {
        Path dump = destination.resolve(entry.getValue() + ".dump");
        privateFile(dump);
        var p =
            command(
                    entry.getKey(),
                    "exec pg_dump -U \"$POSTGRES_USER\" -d \"$1\" -Fc",
                    entry.getValue())
                .redirectOutput(dump.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        if (p.start().waitFor() != 0)
          throw new IllegalStateException("Backup failed: " + entry.getValue());
      }
      System.out.println("Backup created: " + destination.toAbsolutePath());
    } else {
      for (var entry : DATABASES.entrySet()) {
        Path dump = dir.resolve(entry.getValue() + ".dump");
        if (!Files.isRegularFile(dump) || Files.size(dump) == 0)
          throw new IllegalArgumentException("Missing dump: " + dump);
        String temporary = "restore_check_" + UUID.randomUUID().toString().replace("-", "");
        boolean created = false;
        try {
          run(command(entry.getKey(), "exec createdb -U \"$POSTGRES_USER\" \"$1\"", temporary));
          created = true;
          var restore =
              command(
                  entry.getKey(),
                  "exec pg_restore --exit-on-error --no-owner --no-acl -U \"$POSTGRES_USER\" -d"
                      + " \"$1\"",
                  temporary);
          restore
              .redirectInput(dump.toFile())
              .redirectOutput(ProcessBuilder.Redirect.INHERIT)
              .redirectError(ProcessBuilder.Redirect.INHERIT);
          if (restore.start().waitFor() != 0)
            throw new IllegalStateException("Restore drill failed");
          run(
              command(
                  entry.getKey(),
                  "exec psql -v ON_ERROR_STOP=1 -U \"$POSTGRES_USER\" -d \"$1\" -c 'SELECT count(*)"
                      + " AS applied_migrations FROM flyway_schema_history'",
                  temporary));
          System.out.println(
              "PASS restored " + entry.getValue() + " into an isolated temporary database");
        } finally {
          if (created)
            run(command(entry.getKey(), "exec dropdb -U \"$POSTGRES_USER\" \"$1\"", temporary));
        }
      }
    }
  }

  static ProcessBuilder command(String service, String script, String argument) {
    return new ProcessBuilder(
        "docker", "compose", "exec", "-T", service, "sh", "-c", script, "backup-tool", argument);
  }

  static void run(ProcessBuilder p) throws Exception {
    if (p.inheritIO().start().waitFor() != 0)
      throw new IllegalStateException("Database command failed");
  }

  static void privateFile(Path p) throws Exception {
    if (Files.getFileStore(p.getParent()).supportsFileAttributeView("posix"))
      Files.createFile(
          p, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    else Files.createFile(p);
  }
}
