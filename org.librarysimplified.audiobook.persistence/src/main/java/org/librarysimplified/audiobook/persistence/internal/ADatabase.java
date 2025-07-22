
package org.librarysimplified.audiobook.persistence.internal;

import static com.io7m.trasco.api.TrExecutorUpgrade.PERFORM_UPGRADES;
import static java.math.BigInteger.valueOf;
import static java.time.ZoneOffset.UTC;

import com.io7m.anethum.api.ParsingException;
import com.io7m.jxe.core.JXEHardenedSAXParsers;
import com.io7m.trasco.api.TrArguments;
import com.io7m.trasco.api.TrEventExecutingSQL;
import com.io7m.trasco.api.TrEventType;
import com.io7m.trasco.api.TrEventUpgrading;
import com.io7m.trasco.api.TrException;
import com.io7m.trasco.api.TrExecutorConfiguration;
import com.io7m.trasco.api.TrSchemaRevisionSet;
import com.io7m.trasco.vanilla.TrExecutors;
import com.io7m.trasco.vanilla.TrSchemaRevisionSetParsers;

import org.apache.xerces.jaxp.SAXParserFactoryImpl;
import org.librarysimplified.audiobook.api.PlayerBookID;
import org.librarysimplified.audiobook.api.PlayerPosition;
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID;
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem;
import org.librarysimplified.audiobook.persistence.ADatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteOpenMode;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A database based on SQLite.
 */

public final class ADatabase
  implements ADatabaseType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(ADatabase.class);

  private static final String DATABASE_APPLICATION_ID =
    "org.thepalaceproject.audiobook";

  private static final int DATABASE_SQLITE_ID =
    0x50504142;

  private final SQLiteDataSource dataSource;
  private final AtomicBoolean closed;

  private ADatabase(
    final SQLiteDataSource inDataSource)
  {
    this.dataSource =
      Objects.requireNonNull(inDataSource, "dataSource");
    this.closed =
      new AtomicBoolean(false);
  }

  private static void schemaVersionSet(
    final BigInteger version,
    final Connection connection)
    throws SQLException
  {
    final String statementText;
    if (Objects.equals(version, BigInteger.ZERO)) {
      statementText = "insert into schema_version (version_application_id, version_number) values (?, ?)";
      try (var statement =
             connection.prepareStatement(statementText)) {
        statement.setString(1, DATABASE_APPLICATION_ID);
        statement.setLong(2, version.longValue());
        statement.execute();
      }
    } else {
      statementText = "update schema_version set version_number = ?";
      try (var statement =
             connection.prepareStatement(statementText)) {
        statement.setLong(1, version.longValue());
        statement.execute();
      }
    }
  }

  private static Optional<BigInteger> schemaVersionGet(
    final Connection connection)
    throws SQLException
  {
    Objects.requireNonNull(connection, "connection");

    try {
      final var statementText =
        "SELECT version_application_id, version_number FROM schema_version";
      LOG.debug("execute: {}", statementText);

      try (var statement = connection.prepareStatement(statementText)) {
        try (var result = statement.executeQuery()) {
          if (!result.next()) {
            throw new SQLException("schema_version table is empty!");
          }
          final var applicationCA =
            result.getString(1);
          final var version =
            result.getLong(2);

          if (!Objects.equals(applicationCA, DATABASE_APPLICATION_ID)) {
            throw new SQLException(
              String.format(
                "Database application ID is %s but should be %s",
                applicationCA,
                DATABASE_APPLICATION_ID
              )
            );
          }

          return Optional.of(valueOf(version));
        }
      }
    } catch (final SQLException e) {
      if (e.getErrorCode() == SQLiteErrorCode.SQLITE_ERROR.code) {
        connection.rollback();
        return Optional.empty();
      }
      throw e;
    }
  }

  /**
   * Open an SQLite database.
   *
   * @param file The database file
   *
   * @return The database
   *
   * @throws IOException On errors
   */

  public static ADatabaseType open(
    final Path file)
    throws IOException
  {
    try {
      final var absFile = file.toAbsolutePath();
      createOrUpgrade(absFile);
      return doOpen(absFile);
    } catch (final Exception e) {
      throw new IOException(e);
    }
  }

  private static ADatabaseType doOpen(
    final Path file)
  {
    final var url = new StringBuilder(128);
    url.append("jdbc:sqlite:");
    url.append(file);

    final var config = new SQLiteConfig();
    config.setApplicationId(DATABASE_SQLITE_ID);
    config.enforceForeignKeys(true);
    config.setLockingMode(SQLiteConfig.LockingMode.NORMAL);
    config.setJournalMode(SQLiteConfig.JournalMode.WAL);

    final var dataSource = new SQLiteDataSource(config);
    dataSource.setUrl(url.toString());
    return new ADatabase(dataSource);
  }

  private static void setWALMode(
    final Connection connection)
    throws SQLException
  {
    try (var st = connection.createStatement()) {
      st.execute("PRAGMA journal_mode=WAL;");
    }
  }

  private static void createOrUpgrade(
    final Path file)
    throws SQLException, TrException, IOException, ParsingException
  {
    final var url = new StringBuilder(128);
    url.append("jdbc:sqlite:");
    url.append(file);

    final var config = new SQLiteConfig();
    config.setApplicationId(DATABASE_SQLITE_ID);
    config.enforceForeignKeys(true);
    config.setOpenMode(SQLiteOpenMode.CREATE);
    config.setLockingMode(SQLiteConfig.LockingMode.NORMAL);
    config.setJournalMode(SQLiteConfig.JournalMode.WAL);

    final var dataSource = new SQLiteDataSource(config);
    dataSource.setUrl(url.toString());

    final var parsers = new TrSchemaRevisionSetParsers();
    final TrSchemaRevisionSet revisions;
    try (var stream = ADatabase.class.getResourceAsStream(
      "/org/librarysimplified/audiobook/persistence/internal/database.xml")) {
      try (var parser = parsers.createParserWithContext(
        new JXEHardenedSAXParsers(SAXParserFactoryImpl::new),
        URI.create("urn:source"),
        stream,
        status -> LOG.trace("Parser: {}", status)
      )) {
        revisions = parser.execute();
      }
    }

    final var arguments =
      new TrArguments(Map.of());

    try (var connection = dataSource.getConnection()) {
      setWALMode(connection);
      connection.setAutoCommit(false);

      new TrExecutors().create(
        new TrExecutorConfiguration(
          ADatabase::schemaVersionGet,
          ADatabase::schemaVersionSet,
          ADatabase::logEvent,
          revisions,
          PERFORM_UPGRADES,
          arguments,
          connection
        )
      ).execute();
      connection.commit();
    }
  }

  private static void logEvent(
    final TrEventType event)
  {
    if (event instanceof TrEventExecutingSQL sql) {
      LOG.trace("Executing SQL: {}", sql.statement());
    } else if (event instanceof TrEventUpgrading upgrading) {
      LOG.info(
        "Upgrading schema: {} -> {}",
        upgrading.fromVersion(),
        upgrading.toVersion()
      );
    }
  }

  private Connection connection()
    throws SQLException
  {
    final var connection = this.dataSource.getConnection();
    setWALMode(connection);
    connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    connection.setAutoCommit(false);
    return connection;
  }

  @Override
  public void close()
  {
    if (this.closed.compareAndSet(false, true)) {
      // Nothing to close, currently
    }
  }

  private static final String LAST_READ_POSITION_GET = """
    SELECT
      last_read_positions.lr_item_id,
      last_read_positions.lr_time_milliseconds
    FROM
      last_read_positions
    WHERE
      last_read_positions.lr_book_id = ?
    LIMIT 1
    """;

  @Override
  public Optional<PlayerPosition> lastReadPositionGet(
    final PlayerBookID bookID) {
    Objects.requireNonNull(bookID, "bookID");

    try (final var connection = this.connection()) {
      try (final var st = connection.prepareStatement(LAST_READ_POSITION_GET)) {
        st.setString(1, bookID.getValue());
        try (final var rs = st.executeQuery()) {
          while (rs.next()) {
            return Optional.of(
              new PlayerPosition(
                new PlayerManifestReadingOrderID(rs.getString("lr_item_id")),
                new PlayerMillisecondsReadingOrderItem(rs.getLong("lr_time_milliseconds"))
              )
            );
          }
        }
      }
    } catch (final SQLException e) {
      LOG.debug("Failed to read last-read position: ", e);
      return Optional.empty();
    }

    return Optional.empty();
  }

  private static final String LAST_READ_POSITION_SAVE = """
    INSERT INTO last_read_positions (
      lr_book_id,
      lr_item_id,
      lr_time_milliseconds,
      lr_time_updated
    ) VALUES (
      ?,
      ?,
      ?,
      ?
    ) ON CONFLICT DO UPDATE SET
      lr_item_id           = ?,
      lr_time_milliseconds = ?,
      lr_time_updated      = ?
    """;

  @Override
  public void lastReadPositionSave(
    final PlayerBookID bookID,
    final PlayerPosition position) {
    Objects.requireNonNull(bookID, "bookID");
    Objects.requireNonNull(position, "position");

    final var nowTime =
      OffsetDateTime.now(UTC).toString();
    final var itemText =
      position.getReadingOrderID().getText();
    final var offsetTime =
      position.getOffsetMilliseconds().getValue();

    try (final var connection = this.connection()) {
      try (final var st = connection.prepareStatement(LAST_READ_POSITION_SAVE)) {
        st.setString(1, bookID.getValue());
        st.setString(2, itemText);
        st.setLong(3, offsetTime);
        st.setString(4, nowTime);

        st.setString(5, itemText);
        st.setLong(6, offsetTime);
        st.setString(7, nowTime);
        st.execute();
        connection.commit();
      }
    } catch (final SQLException e) {
      LOG.debug("Failed to save last-read position: ", e);
    }
  }
}
