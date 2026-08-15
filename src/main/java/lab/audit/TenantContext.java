package lab.audit;

import java.util.Optional;

final class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    static void enter(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        CURRENT_TENANT.set(tenantId);
    }

    static Optional<String> currentTenant() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    static String currentTenantLabel() {
        return currentTenant().orElse("SYSTEM");
    }

    static void clear() {
        CURRENT_TENANT.remove();
    }
}
