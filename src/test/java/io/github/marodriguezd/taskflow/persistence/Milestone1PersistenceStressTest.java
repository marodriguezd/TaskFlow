package io.github.marodriguezd.taskflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.marodriguezd.taskflow.domain.HistoryEventType;
import io.github.marodriguezd.taskflow.domain.HistoryItem;
import io.github.marodriguezd.taskflow.domain.Priority;
import io.github.marodriguezd.taskflow.domain.Task;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Milestone1PersistenceStressTest {

    @Nested
    @DisplayName("1. Concurrency and Connection Lifecycle")
    class LifecycleAndConcurrencyTests {

        @TempDir Path tempDir;

        @Test
        @DisplayName(
                "DatabaseManager throws SQLException on getConnection after close on memory DB")
        void getConnectionThrowsAfterCloseOnMemoryDb() throws Exception {
            DatabaseManager manager = DatabaseManager.inMemory();
            assertThat(manager.isClosed()).isFalse();

            manager.close();
            assertThat(manager.isClosed()).isTrue();

            assertThatThrownBy(manager::getConnection)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("DatabaseManager is closed");

            assertThatThrownBy(() -> manager.inTransaction(conn -> "result"))
                    .isInstanceOf(PersistenceException.class)
                    .hasCauseInstanceOf(SQLException.class);
        }

        @Test
        @DisplayName("DatabaseManager throws SQLException on getConnection after close on disk DB")
        void getConnectionThrowsAfterCloseOnDiskDb() throws Exception {
            DatabaseManager manager = new DatabaseManager(tempDir.resolve("disk-close.db"));
            assertThat(manager.isClosed()).isFalse();

            manager.close();
            assertThat(manager.isClosed()).isTrue();

            assertThatThrownBy(manager::getConnection)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("DatabaseManager is closed");

            assertThatThrownBy(() -> manager.inTransaction(conn -> "result"))
                    .isInstanceOf(PersistenceException.class)
                    .hasCauseInstanceOf(SQLException.class);
        }

        @Test
        @DisplayName("close is thread-safe and idempotent under high concurrency (50 threads)")
        void closeIsThreadSafeAndIdempotent() throws Exception {
            DatabaseManager manager = DatabaseManager.inMemory();
            int threadCount = 50;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    barrier.await();
                                    manager.close();
                                    return null;
                                }));
            }

            for (Future<Void> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            pool.shutdown();

            assertThat(manager.isClosed()).isTrue();
            // Subsequent sequential close calls are safe no-ops
            manager.close();
            manager.close();
            assertThat(manager.isClosed()).isTrue();

            assertThatThrownBy(manager::getConnection)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("DatabaseManager is closed");
        }

        @Test
        @DisplayName("Concurrent threads acquiring connections while closed fail gracefully")
        void concurrentThreadsAcquiringConnectionsWhileClosedFailGracefully() throws Exception {
            DatabaseManager manager = DatabaseManager.inMemory();
            manager.close();
            assertThat(manager.isClosed()).isTrue();

            int threadCount = 50;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            AtomicInteger sqlExceptionCount = new AtomicInteger();
            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    barrier.await();
                                    try {
                                        manager.getConnection();
                                    } catch (SQLException e) {
                                        if (e.getMessage().contains("DatabaseManager is closed")) {
                                            sqlExceptionCount.incrementAndGet();
                                        }
                                    }
                                    return null;
                                }));
            }

            for (Future<Void> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            pool.shutdown();

            assertThat(sqlExceptionCount.get())
                    .as("All 50 threads should catch the closed SQLException")
                    .isEqualTo(threadCount);
        }

        @Test
        @DisplayName("Concurrent transactions during asynchronous close fail or succeed cleanly")
        void concurrentTransactionsDuringAsynchronousClose() throws Exception {
            DatabaseManager manager = new DatabaseManager(tempDir.resolve("async-close.db"));
            int workerCount = 20;
            ExecutorService pool = Executors.newFixedThreadPool(workerCount + 1);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successfulTransactions = new AtomicInteger();
            AtomicInteger failedAfterClose = new AtomicInteger();
            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < workerCount; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    startLatch.await();
                                    for (int j = 0; j < 50; j++) {
                                        try {
                                            manager.inTransaction(
                                                    conn -> {
                                                        try (Statement stmt =
                                                                conn.createStatement()) {
                                                            stmt.execute("SELECT 1");
                                                        }
                                                        return null;
                                                    });
                                            successfulTransactions.incrementAndGet();
                                        } catch (PersistenceException e) {
                                            assertThat(e.getCause())
                                                    .isInstanceOf(SQLException.class);
                                            failedAfterClose.incrementAndGet();
                                        }
                                    }
                                    return null;
                                }));
            }

            // Close thread
            Future<Void> closeFuture =
                    pool.submit(
                            () -> {
                                startLatch.await();
                                Thread.sleep(15);
                                manager.close();
                                return null;
                            });

            startLatch.countDown();
            closeFuture.get(5, TimeUnit.SECONDS);
            for (Future<Void> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            pool.shutdown();

            assertThat(manager.isClosed()).isTrue();
            assertThat(successfulTransactions.get() + failedAfterClose.get())
                    .isEqualTo(workerCount * 50);
        }
    }

    @Nested
    @DisplayName("2. SqliteTaskRepository archiveCompletion Idempotency")
    class ArchiveCompletionIdempotencyTests {

        @Test
        @DisplayName("Sequential multiple calls to archiveCompletion on same task do not duplicate")
        void sequentialArchiveCompletionDoesNotDuplicate() {
            try (DatabaseManager db = DatabaseManager.inMemory()) {
                SqliteTaskRepository taskRepo = new SqliteTaskRepository(db);
                SqliteHistoryRepository historyRepo = new SqliteHistoryRepository(db);

                Task task = taskRepo.save(Task.create("Sequential Task", Priority.HIGH, 600));
                HistoryItem item1 = HistoryItem.fromTask(task, HistoryEventType.COMPLETED, false);
                HistoryItem item2 = HistoryItem.fromTask(task, HistoryEventType.COMPLETED, false);

                // Call 1: Success
                taskRepo.archiveCompletion(task, item1);
                assertThat(historyRepo.count()).isEqualTo(1);
                assertThat(taskRepo.findById(task.id()).orElseThrow().remainingSeconds()).isZero();

                // Call 2..5: Should be completely ignored
                for (int i = 0; i < 4; i++) {
                    taskRepo.archiveCompletion(task, item2);
                }
                assertThat(historyRepo.count()).isEqualTo(1);
            }
        }

        @TempDir Path tempDir;

        @Test
        @DisplayName(
                "Concurrent hammer (30 threads) calling archiveCompletion on same task records exactly 1 history row")
        void concurrentArchiveCompletionHammerOnSameTask() throws Exception {
            try (DatabaseManager db = new DatabaseManager(tempDir.resolve("hammer.db"))) {
                SqliteTaskRepository taskRepo = new SqliteTaskRepository(db);
                SqliteHistoryRepository historyRepo = new SqliteHistoryRepository(db);

                Task task = taskRepo.save(Task.create("Hammered Task", Priority.HIGH, 1200));
                HistoryItem item = HistoryItem.fromTask(task, HistoryEventType.COMPLETED, false);

                int threadCount = 30;
                ExecutorService pool = Executors.newFixedThreadPool(threadCount);
                CyclicBarrier barrier = new CyclicBarrier(threadCount);
                List<Future<Void>> futures = new ArrayList<>();

                for (int i = 0; i < threadCount; i++) {
                    futures.add(
                            pool.submit(
                                    () -> {
                                        barrier.await();
                                        taskRepo.archiveCompletion(task, item);
                                        return null;
                                    }));
                }

                for (Future<Void> future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }
                pool.shutdown();

                assertThat(historyRepo.count())
                        .as("Exactly 1 history entry must be inserted despite 30 concurrent calls")
                        .isEqualTo(1);

                Task finishedTask = taskRepo.findById(task.id()).orElseThrow();
                assertThat(finishedTask.remainingSeconds()).isZero();
            }
        }

        @Test
        @DisplayName(
                "Concurrent archiveCompletion across 10 distinct tasks records exactly 10 history rows")
        void concurrentArchiveCompletionAcrossMultipleTasks() throws Exception {
            try (DatabaseManager db = new DatabaseManager(tempDir.resolve("multi-hammer.db"))) {
                SqliteTaskRepository taskRepo = new SqliteTaskRepository(db);
                SqliteHistoryRepository historyRepo = new SqliteHistoryRepository(db);

                int taskCount = 10;
                List<Task> tasks = new ArrayList<>();
                for (int i = 0; i < taskCount; i++) {
                    tasks.add(taskRepo.save(Task.create("Multi-Task " + i, Priority.MEDIUM, 600)));
                }

                int threadsPerTask = 5;
                int totalThreads = taskCount * threadsPerTask;
                ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
                CyclicBarrier barrier = new CyclicBarrier(totalThreads);
                List<Future<Void>> futures = new ArrayList<>();

                for (Task t : tasks) {
                    HistoryItem item = HistoryItem.fromTask(t, HistoryEventType.COMPLETED, false);
                    for (int j = 0; j < threadsPerTask; j++) {
                        futures.add(
                                pool.submit(
                                        () -> {
                                            barrier.await();
                                            taskRepo.archiveCompletion(t, item);
                                            return null;
                                        }));
                    }
                }

                for (Future<Void> future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }
                pool.shutdown();

                assertThat(historyRepo.count())
                        .as("Exactly 10 history entries should exist for 10 tasks")
                        .isEqualTo(taskCount);

                for (Task t : tasks) {
                    assertThat(taskRepo.findById(t.id()).orElseThrow().remainingSeconds()).isZero();
                }
            }
        }

        @Test
        @DisplayName("archiveCompletion on non-existent task skips history insert without throwing")
        void archiveCompletionOnNonExistentTask() {
            try (DatabaseManager db = DatabaseManager.inMemory()) {
                SqliteTaskRepository taskRepo = new SqliteTaskRepository(db);
                SqliteHistoryRepository historyRepo = new SqliteHistoryRepository(db);

                Task ghost =
                        new Task(
                                999999L,
                                "Ghost",
                                Priority.LOW,
                                600,
                                600,
                                java.time.Instant.now(),
                                java.time.Instant.now());
                HistoryItem item = HistoryItem.fromTask(ghost, HistoryEventType.COMPLETED, false);

                taskRepo.archiveCompletion(ghost, item);
                assertThat(historyRepo.count()).isZero();
            }
        }

        @Test
        @DisplayName("archiveCompletion on task already at 0 seconds skips history insert")
        void archiveCompletionOnTaskAlreadyAtZero() {
            try (DatabaseManager db = DatabaseManager.inMemory()) {
                SqliteTaskRepository taskRepo = new SqliteTaskRepository(db);
                SqliteHistoryRepository historyRepo = new SqliteHistoryRepository(db);

                Task task = taskRepo.save(Task.create("Zero Task", Priority.LOW, 600));
                taskRepo.updateRemainingSeconds(task.id(), 0);

                HistoryItem item = HistoryItem.fromTask(task, HistoryEventType.COMPLETED, false);

                taskRepo.archiveCompletion(task, item);
                assertThat(historyRepo.count()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("3. Database Pragmas on Disk and In-Memory")
    class PragmasTests {

        @TempDir Path tempDir;

        @Test
        @DisplayName(
                "Disk database has WAL, synchronous NORMAL, foreign keys ON, busy timeout 3000")
        void diskDatabasePragmas() throws Exception {
            Path dbPath = tempDir.resolve("pragma-stress.db");
            try (DatabaseManager db = new DatabaseManager(dbPath);
                    Connection conn = db.getConnection();
                    Statement stmt = conn.createStatement()) {

                try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualToIgnoringCase("wal");
                }
                try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
                try (ResultSet rs = stmt.executeQuery("PRAGMA busy_timeout")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(3000);
                }
                try (ResultSet rs = stmt.executeQuery("PRAGMA synchronous")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1); // 1 = NORMAL
                }
            }

            // Re-opening existing database file preserves pragmas and data
            try (DatabaseManager db2 = new DatabaseManager(dbPath);
                    Connection conn = db2.getConnection();
                    Statement stmt = conn.createStatement()) {

                try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualToIgnoringCase("wal");
                }
                try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
                try (ResultSet rs = stmt.executeQuery("PRAGMA synchronous")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }

        @Test
        @DisplayName("In-memory database has memory journal, foreign keys ON, busy timeout 3000")
        void inMemoryDatabasePragmas() throws Exception {
            try (DatabaseManager db = DatabaseManager.inMemory();
                    Connection conn = db.getConnection();
                    Statement stmt = conn.createStatement()) {

                try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualToIgnoringCase("memory");
                }
                try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
                try (ResultSet rs = stmt.executeQuery("PRAGMA busy_timeout")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(3000);
                }
            }
        }

        @Test
        @DisplayName(
                "In-memory database shared cache connects properly across multiple connections")
        void inMemorySharedCacheWorksAcrossConnections() throws Exception {
            try (DatabaseManager db = DatabaseManager.inMemory()) {
                try (Connection conn1 = db.getConnection();
                        Statement stmt1 = conn1.createStatement()) {
                    stmt1.execute(
                            "INSERT INTO tasks (name, priority, total_seconds, remaining_seconds, created_at, updated_at) "
                                    + "VALUES ('Shared Task', 'HIGH', 600, 600, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
                }

                try (Connection conn2 = db.getConnection();
                        Statement stmt2 = conn2.createStatement();
                        ResultSet rs =
                                stmt2.executeQuery(
                                        "SELECT name FROM tasks WHERE name = 'Shared Task'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("Shared Task");
                }
            }
        }
    }
}
