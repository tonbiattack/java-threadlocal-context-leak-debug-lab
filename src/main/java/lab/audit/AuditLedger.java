package lab.audit;

import java.util.ArrayList;
import java.util.List;

final class AuditLedger {
    private final List<AuditEvent> events = new ArrayList<>();

    AuditEvent append(String operation, String orderId, String attributedTenant, String workerName) {
        AuditEvent event = new AuditEvent(operation, orderId, attributedTenant, workerName);
        events.add(event);
        return event;
    }

    List<AuditEvent> snapshot() {
        return List.copyOf(events);
    }
}
