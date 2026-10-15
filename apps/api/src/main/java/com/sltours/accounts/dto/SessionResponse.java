package com.sltours.accounts.dto;
public record SessionResponse(boolean authenticated, String email, String name, String role, boolean emailVerified) {
    public SessionResponse(boolean authenticated, String email, String name, String role) { this(authenticated, email, name, role, false); }
    public static SessionResponse anonymous() { return new SessionResponse(false, null, null, null, false); }
}
