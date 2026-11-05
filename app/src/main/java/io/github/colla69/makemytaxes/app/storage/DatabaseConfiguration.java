package io.github.colla69.makemytaxes.app.storage;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DatabaseConfiguration {

  @Bean
  DataSource dataSource(
      @Value("${makemytaxes.database.path}") Path file,
      @Value("${makemytaxes.database.passphrase:}") String passphrase)
      throws IOException {
    if (passphrase.isBlank()) {
      throw new IllegalStateException(
          "No database passphrase. Set the environment variable"
              + " MAKEMYTAXES_DATABASE_PASSPHRASE and start the app again.");
    }
    Files.createDirectories(file.toAbsolutePath().getParent());

    var pool = new HikariDataSource();
    pool.setDataSource(EncryptedDatabase.dataSource(file, passphrase));
    // SQLite allows one writer at a time, and the app has at most two users.
    pool.setMaximumPoolSize(1);
    return pool;
  }
}
