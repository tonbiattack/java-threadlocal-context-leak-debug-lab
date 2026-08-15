package lab.audit;

public record AuditEvent(String operation, String orderId, String attributedTenant, String workerName) {
}
