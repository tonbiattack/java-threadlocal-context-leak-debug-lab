package lab.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class AuditJobRunner implements AutoCloseable {
    private final ExecutorService executor;
    private final AuditLedger ledger = new AuditLedger();
    private final List<String> trace = new ArrayList<>();

    public AuditJobRunner() {
        executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "audit-worker-1"));
    }

    public JobResult runTenantExport(String tenantId, String orderId) {
        return await(executor.submit(() -> {
            TenantContext.enter(tenantId);
            try {
                trace("tenant-export-entry");
                return record("TENANT_EXPORT", orderId);
            } finally {
                TenantContext.clear();
            }
        }));
    }

    public JobResult runSystemReconciliation(String orderId) {
        return await(executor.submit(() -> {
            trace("system-reconciliation-entry");
            return record("SYSTEM_RECONCILIATION", orderId);
        }));
    }

    public List<AuditEvent> auditEvents() {
        return ledger.snapshot();
    }

    public List<String> traceLines() {
        return List.copyOf(trace);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private JobResult record(String operation, String orderId) {
        String attributedTenant = TenantContext.currentTenantLabel();
        String workerName = Thread.currentThread().getName();
        ledger.append(operation, orderId, attributedTenant, workerName);
        return new JobResult(operation, orderId, attributedTenant, workerName);
    }

    private void trace(String phase) {
        trace.add("TRACE phase=%s worker=%s context=%s".formatted(
                phase,
                Thread.currentThread().getName(),
                TenantContext.currentTenantLabel()));
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("job execution was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("job execution failed", exception.getCause());
        }
    }
}
