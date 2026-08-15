package lab.audit;

import java.util.ArrayList;
import java.util.List;

public final class AuditJobRunnerTest {
    private AuditJobRunnerTest() {
    }

    public static void main(String[] args) {
        List<TestCase> testCases = List.of(
                new TestCase("tenant_job_is_attributed_to_its_tenant", AuditJobRunnerTest::tenantJobIsAttributedToItsTenant),
                new TestCase("unscoped_job_uses_system_when_worker_has_no_history", AuditJobRunnerTest::unscopedJobUsesSystemWhenWorkerHasNoHistory),
                new TestCase("unscoped_job_is_not_attributed_to_previous_tenant", AuditJobRunnerTest::unscopedJobIsNotAttributedToPreviousTenant));

        int failures = 0;
        for (TestCase testCase : testCases) {
            try {
                testCase.body().run();
                System.out.println("PASS " + testCase.name());
            } catch (AssertionError error) {
                failures++;
                System.err.println("FAIL " + testCase.name());
                System.err.println(error.getMessage());
            }
        }

        if (failures > 0) {
            throw new AssertionError("%d test(s) failed".formatted(failures));
        }
    }

    private static void tenantJobIsAttributedToItsTenant() {
        try (AuditJobRunner runner = new AuditJobRunner()) {
            JobResult result = runner.runTenantExport("tenant-a", "order-100");

            assertEquals("tenant job result", "tenant-a", result.attributedTenant());
            assertEquals("tenant ledger state", "tenant-a", runner.auditEvents().get(0).attributedTenant());
        }
    }

    private static void unscopedJobUsesSystemWhenWorkerHasNoHistory() {
        try (AuditJobRunner runner = new AuditJobRunner()) {
            JobResult result = runner.runSystemReconciliation("order-200");

            assertEquals("fresh worker result", "SYSTEM", result.attributedTenant());
            assertEquals("fresh worker ledger state", "SYSTEM", runner.auditEvents().get(0).attributedTenant());
        }
    }

    private static void unscopedJobIsNotAttributedToPreviousTenant() {
        List<String> failures = new ArrayList<>();
        try (AuditJobRunner runner = new AuditJobRunner()) {
            runner.runTenantExport("tenant-a", "order-100");
            JobResult result = runner.runSystemReconciliation("order-200");
            AuditEvent finalEvent = runner.auditEvents().get(1);

            collectEquals(failures, "direct job result", "SYSTEM", result.attributedTenant());
            collectEquals(failures, "persisted audit event", "SYSTEM", finalEvent.attributedTenant());
            collectEquals(failures, "worker reuse", "audit-worker-1", result.workerName());
        }

        if (!failures.isEmpty()) {
            throw new AssertionError(String.join(System.lineSeparator(), failures));
        }
    }

    private static void collectEquals(List<String> failures, String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            failures.add("  " + label + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("  " + label + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private record TestCase(String name, ThrowingRunnable body) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
