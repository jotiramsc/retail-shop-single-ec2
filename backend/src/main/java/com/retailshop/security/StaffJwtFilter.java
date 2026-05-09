package com.retailshop.security;

import com.retailshop.entity.StaffUser;
import com.retailshop.enums.AppPermission;
import com.retailshop.service.StaffUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class StaffJwtFilter extends OncePerRequestFilter {

    private final StaffJwtService staffJwtService;
    private final StaffUserService staffUserService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                staffJwtService.parseUsername(authorization.substring("Bearer ".length()))
                        .ifPresent(this::authenticateStaffUser);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateStaffUser(String username) {
        try {
            StaffUser user = staffUserService.getByUsername(username);
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                return;
            }
            List<SimpleGrantedAuthority> authorities = Stream.concat(
                            Stream.of("ROLE_" + user.getRole().name()),
                            staffUserService.getEffectivePermissions(user)
                                    .stream()
                                    .map(AppPermission::name)
                                    .map(permission -> "PERM_" + permission))
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities));
        } catch (RuntimeException ignored) {
            SecurityContextHolder.clearContext();
        }
    }
}
