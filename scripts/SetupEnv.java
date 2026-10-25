import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;

/** Run from project root with JDK 17+: java scripts/SetupEnv.java */
class SetupEnv {
  public static void main(String[] args) throws Exception {
    Path root = Path.of("").toAbsolutePath();
    if (!Files.isRegularFile(root.resolve("compose.yaml"))
        || !Files.isRegularFile(root.resolve("pom.xml")))
      throw new IllegalStateException(
          "Run from the project root containing compose.yaml and pom.xml.");
    Map<String, String> values = new LinkedHashMap<>();
    SecureRandom random = new SecureRandom();
    values.put("QUESTION_DB_USERNAME", "question_app");
    values.put("QUESTION_DB_PASSWORD", secret(random, 24, false));
    values.put("QUIZ_DB_USERNAME", "quiz_app");
    values.put("QUIZ_DB_PASSWORD", secret(random, 24, false));
    var generator = java.security.KeyPairGenerator.getInstance("RSA");
    generator.initialize(3072);
    var keys = generator.generateKeyPair();
    String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
    String privateKey = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
    if (args.length == 1 && args[0].equals("--keys-only")) {
      System.out.println("JWT_PUBLIC_KEY=" + publicKey);
      System.out.println("JWT_PRIVATE_KEY=" + privateKey);
      return;
    }
    values.put("JWT_PUBLIC_KEY", publicKey);
    values.put("JWT_PRIVATE_KEY", privateKey);
    values.put("ACCOUNT_REQUIRE_VERIFICATION", "false");
    values.put("ACCOUNT_MAIL_ENABLED", "false");
    values.put("COOKIE_SECURE", "false");
    values.put("PUBLIC_APP_URL", "http://localhost:8765");
    StringBuilder contents = new StringBuilder();
    values.forEach((k, v) -> contents.append(k).append('=').append(v).append('\n'));
    Path path = root.resolve(".env");
    try {
      if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
        try (var channel =
            Files.newByteChannel(
                path,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")))) {
          var bytes =
              java.nio.ByteBuffer.wrap(
                  contents.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
          while (bytes.hasRemaining()) channel.write(bytes);
        }
      } else
        Files.writeString(path, contents, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    } catch (FileAlreadyExistsException ex) {
      System.err.println(
          ".env already exists; refusing to overwrite credentials or database passwords.");
      System.exit(1);
    }
    System.out.println("Created .env. Keep it private. Create your account on the website.");
    System.out.println("Next: docker compose up --build -d");
  }

  private static String secret(SecureRandom random, int size, boolean standard) {
    byte[] bytes = new byte[size];
    random.nextBytes(bytes);
    return (standard ? Base64.getEncoder() : Base64.getUrlEncoder().withoutPadding())
        .encodeToString(bytes);
  }
}
