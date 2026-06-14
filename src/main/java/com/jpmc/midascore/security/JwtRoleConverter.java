package com.jpmc.midascore.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * Maps a JWT's {@code roles} claim to Spring Security authorities (RBAC).
 *
 * <p>Each role {@code R} becomes {@code ROLE_R}, so {@code @PreAuthorize("hasRole('TELLER')")}
 * works. Missing or malformed claims yield no authorities (the request is then unauthorized
 * for any role-guarded endpoint) rather than throwing.
 */
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object roles = jwt.getClaim("roles");
        if (!(roles instanceof Collection<?> roleList)) {
            return List.of();
        }
        return roleList.stream()
                .map(String::valueOf)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
