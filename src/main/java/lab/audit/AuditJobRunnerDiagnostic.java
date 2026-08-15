package lab.audit;

public final class AuditJobRunnerDiagnostic {
    private AuditJobRunnerDiagnostic() {
    }

    public static void main(String[] args) {
        try (AuditJobRunner runner = new AuditJobRunner()) {
            JobResult tenantJob = runner.runTenantExport("tenant-a", "order-100");
            JobResult systemJob = runner.runSystemReconciliation("order-200");

            System.out.println("RESULT tenantJob=" + tenantJob);
            System.out.println("RESULT systemJob=" + systemJob);
            runner.traceLines().forEach(System.out::println);
            runner.auditEvents().forEach(event -> System.out.println("LEDGER " + event));
        }
    }
}
