package com.tbm.security;

import com.tbm.user.AppUser;
import com.tbm.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the authenticated principal from a {@code Authorization: Bearer <jwt>} header. Any
 * malformed/expired/missing token simply leaves the request unauthenticated — Spring Security's
 * access rules (SecurityConfig) reject it downstream with a 401/403 as appropriate.
 *
 * <p>System Admin standing is resolved fresh from the database on every request — never cached in
 * or derived from the JWT itself — so a revoked admin is blocked starting with their very next
 * request (spec FR-004, research.md §1).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository appUserRepository) {
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            try {
                JwtService.JwtPrincipal principal = jwtService.parseToken(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, authoritiesFor(principal));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> authoritiesFor(JwtService.JwtPrincipal principal) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        boolean isSystemAdmin =
                appUserRepository.findById(principal.userId()).map(AppUser::isSystemAdmin).orElse(false);
        if (isSystemAdmin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
        }
        return authorities;
    }
}
