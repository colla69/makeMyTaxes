package io.github.colla69.makemytaxes.app.storage;

import java.nio.file.Path;
import org.sqlite.SQLiteDataSource;
import org.sqlite.mc.SQLiteMCSqlCipherConfig;

public final class EncryptedDatabase {

  private EncryptedDatabase() {}

  public static SQLiteDataSource dataSource(Path file, String passphrase) {
    var config = SQLiteMCSqlCipherConfig.getV4Defaults().withKey(passphrase).build();
    var dataSource = new SQLiteDataSource(config);
    dataSource.setUrl("jdbc:sqlite:" + file);
    return dataSource;
  }
}
