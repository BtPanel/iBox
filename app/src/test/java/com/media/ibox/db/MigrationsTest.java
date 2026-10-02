package com.media.ibox.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public class MigrationsTest {

    @Test
    public void migration31To35PreservesHistoryAndFavorites() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createVersion31Database(connection);
            SupportSQLiteDatabase database = adapt(connection);
            for (Migration migration : Migrations.ALL) {
                if (migration.startVersion >= 31) migration.migrate(database);
            }

            assertEquals("history-key", stringValue(connection, "SELECT `key` FROM History"));
            assertEquals("favorite-key", stringValue(connection, "SELECT `key` FROM Keep"));
            assertTrue(columns(connection, "Live").contains("keep"));
            assertTrue(columns(connection, "Track").contains("format"));
        }
    }

    private void createVersion31Database(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE History (`key` TEXT NOT NULL PRIMARY KEY, `vodPic` TEXT, `vodName` TEXT, `vodFlag` TEXT, `vodRemarks` TEXT, `episodeUrl` TEXT, `revSort` INTEGER NOT NULL, `revPlay` INTEGER NOT NULL, `createTime` INTEGER NOT NULL, `opening` INTEGER NOT NULL, `ending` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `speed` REAL NOT NULL, `scale` INTEGER NOT NULL, `cid` INTEGER NOT NULL)");
            statement.execute("CREATE TABLE Keep (`key` TEXT NOT NULL PRIMARY KEY, `siteName` TEXT, `vodName` TEXT, `vodPic` TEXT, `createTime` INTEGER NOT NULL, `type` INTEGER NOT NULL, `cid` INTEGER NOT NULL)");
            statement.execute("CREATE TABLE Live (`name` TEXT NOT NULL PRIMARY KEY, `boot` INTEGER NOT NULL, `pass` INTEGER NOT NULL)");
            statement.execute("CREATE TABLE Track (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, `track` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `selected` INTEGER NOT NULL, `adaptive` INTEGER NOT NULL)");
            statement.execute("INSERT INTO History VALUES ('history-key', NULL, 'Movie', NULL, NULL, NULL, 0, 0, 1, 0, 0, 10, 100, 1.0, 0, 7)");
            statement.execute("INSERT INTO Keep VALUES ('favorite-key', 'Site', 'Movie', NULL, 1, 0, 7)");
        }
    }

    private SupportSQLiteDatabase adapt(Connection connection) {
        return (SupportSQLiteDatabase) Proxy.newProxyInstance(
                SupportSQLiteDatabase.class.getClassLoader(),
                new Class[]{SupportSQLiteDatabase.class},
                (proxy, method, args) -> {
                    if ("execSQL".equals(method.getName()) && args != null && args.length == 1) {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute((String) args[0]);
                        }
                        return null;
                    }
                    if (method.getDeclaringClass() == Object.class) return method.invoke(this, args);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private String stringValue(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private Set<String> columns(Connection connection, String table) throws Exception {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) columns.add(result.getString("name"));
        }
        return columns;
    }
}
