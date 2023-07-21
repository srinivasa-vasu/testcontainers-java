package org.testcontainers.containers.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.YugabyteDBYSQLContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.rnorth.ducttape.unreliables.Unreliables.retryUntilSuccess;

/**
 * Custom wait strategy for YSQL API.
 *
 * <p>
 * Though we can either use HTTP or PORT based wait strategy, when we create a custom
 * database/role, it gets executed asynchronously. As the wait on container.start() on a
 * specific port wouldn't fully guarantee the custom object execution. It's better to
 * check the DB status with this way with a smoke test query that uses the underlying
 * custom objects and wait for the operation to complete.
 * </p>
 */
@RequiredArgsConstructor
@Slf4j
public final class YugabyteDBYSQLWaitStrategy extends AbstractWaitStrategy {

    private static final String YSQL_TEST_QUERY = "SELECT 1";
    private static final String YSQL_EXTENDED_PROBE = "CREATE TEMP TABLE IF NOT EXISTS YB_SAMPLE(k int, v int, primary key(k, v))";

    @Override
    public void waitUntilReady() {
        YugabyteDBYSQLContainer container = (YugabyteDBYSQLContainer) waitStrategyTarget;
        retryUntilSuccess(
            (int) startupTimeout.getSeconds(),
            TimeUnit.SECONDS,
            () -> {
                getRateLimiter()
                    .doWhenReady(() -> {
                        try (Connection con = container.createConnection(""); Statement stmt = con.createStatement()) {
                            stmt.execute(container.isExtendedStartupProbe() ? YSQL_EXTENDED_PROBE : YSQL_TEST_QUERY);
                        }
                        catch (SQLException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                return true;
            }
        );
    }
}
