package uy.edu.um.xperience.security;

import java.util.UUID;

/** Resource attributes loaded by the server, never authorization claims from a request body. */
public record ResourceAccess(UUID ownerId, UUID companyId, boolean published) {
    public static ResourceAccess own(UUID ownerId) { return new ResourceAccess(ownerId, null, false); }
    public static ResourceAccess company(UUID companyId) { return new ResourceAccess(null, companyId, false); }
    public static ResourceAccess publication(boolean published) { return new ResourceAccess(null, null, published); }
}
