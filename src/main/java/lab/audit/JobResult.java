package lab.audit;

public record JobResult(String operation, String orderId, String attributedTenant, String workerName) {
}
