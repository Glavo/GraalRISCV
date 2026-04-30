/*
 * This static musl example embeds SQLite and runs a small file-backed
 * analytical workload. It exercises a real third-party C codebase together
 * with heap allocation, file I/O, transactions, indexes, and query execution.
 */

#include <stdio.h>
#include <stdlib.h>

#include "sqlite3.h"

/* Prints a SQLite failure with context and returns a stable nonzero status. */
static int fail(sqlite3 *database, const char *operation) {
    const char *message = database != NULL ? sqlite3_errmsg(database) : "no database";
    printf("%s-failed:%s\n", operation, message);
    return 1;
}

/* Executes one SQL statement and converts failures into stable output. */
static int exec_sql(sqlite3 *database, const char *sql, const char *operation) {
    char *error_message = NULL;
    int result = sqlite3_exec(database, sql, NULL, NULL, &error_message);
    if (result != SQLITE_OK) {
        printf("%s-failed:%s\n", operation, error_message != NULL ? error_message : sqlite3_errmsg(database));
        sqlite3_free(error_message);
        return 1;
    }
    return 0;
}

/* Runs a single-row query with two integer columns. */
static int query_pair(sqlite3 *database, const char *sql, int *left, int *right, const char *operation) {
    sqlite3_stmt *statement = NULL;
    if (sqlite3_prepare_v2(database, sql, -1, &statement, NULL) != SQLITE_OK) {
        return fail(database, operation);
    }

    int result = sqlite3_step(statement);
    if (result != SQLITE_ROW) {
        sqlite3_finalize(statement);
        return fail(database, operation);
    }

    *left = sqlite3_column_int(statement, 0);
    *right = sqlite3_column_int(statement, 1);
    result = sqlite3_finalize(statement);
    if (result != SQLITE_OK) {
        return fail(database, operation);
    }
    return 0;
}

int main(void) {
    sqlite3 *database = NULL;
    if (sqlite3_open("/showcase.db", &database) != SQLITE_OK) {
        int status = fail(database, "open");
        sqlite3_close(database);
        return status;
    }

    if (exec_sql(database, "PRAGMA journal_mode=MEMORY;", "pragma") != 0
            || exec_sql(database, "DROP TABLE IF EXISTS events;", "drop") != 0
            || exec_sql(database, "CREATE TABLE events(id INTEGER PRIMARY KEY, category TEXT NOT NULL, value INTEGER NOT NULL);", "create") != 0
            || exec_sql(database, "BEGIN IMMEDIATE;", "begin") != 0) {
        sqlite3_close(database);
        return 1;
    }

    sqlite3_stmt *insert = NULL;
    if (sqlite3_prepare_v2(database, "INSERT INTO events(category, value) VALUES (?, ?);", -1, &insert, NULL) != SQLITE_OK) {
        int status = fail(database, "prepare-insert");
        sqlite3_close(database);
        return status;
    }

    for (int index = 1; index <= 64; index++) {
        const char *category = (index % 3 == 0) ? "fizz" : ((index % 5 == 0) ? "buzz" : "plain");
        sqlite3_bind_text(insert, 1, category, -1, SQLITE_STATIC);
        sqlite3_bind_int(insert, 2, index * index);
        if (sqlite3_step(insert) != SQLITE_DONE) {
            sqlite3_finalize(insert);
            int status = fail(database, "insert");
            sqlite3_close(database);
            return status;
        }
        sqlite3_reset(insert);
        sqlite3_clear_bindings(insert);
    }

    if (sqlite3_finalize(insert) != SQLITE_OK
            || exec_sql(database, "COMMIT;", "commit") != 0
            || exec_sql(database, "CREATE INDEX events_category_value ON events(category, value);", "index") != 0) {
        sqlite3_close(database);
        return 1;
    }

    int row_count = 0;
    int value_sum = 0;
    if (query_pair(database, "SELECT count(*), sum(value) FROM events;", &row_count, &value_sum, "query-total") != 0) {
        sqlite3_close(database);
        return 1;
    }

    int fizz_count = 0;
    int fizz_sum = 0;
    if (query_pair(database, "SELECT count(*), sum(value) FROM events WHERE category = 'fizz';", &fizz_count, &fizz_sum, "query-fizz") != 0) {
        sqlite3_close(database);
        return 1;
    }

    int recursive_count = 0;
    int recursive_sum = 0;
    if (query_pair(database,
            "WITH RECURSIVE seq(x) AS (VALUES(1) UNION ALL SELECT x + 1 FROM seq WHERE x < 16) SELECT count(*), sum(x * x) FROM seq;",
            &recursive_count,
            &recursive_sum,
            "query-recursive") != 0) {
        sqlite3_close(database);
        return 1;
    }

    printf("sqlite-version=%s\n", sqlite3_libversion());
    printf("rows=%d\n", row_count);
    printf("value-sum=%d\n", value_sum);
    printf("fizz=%d:%d\n", fizz_count, fizz_sum);
    printf("recursive=%d:%d\n", recursive_count, recursive_sum);
    printf("sqlite-showcase-ok\n");

    if (sqlite3_close(database) != SQLITE_OK) {
        return fail(database, "close");
    }
    return 0;
}
