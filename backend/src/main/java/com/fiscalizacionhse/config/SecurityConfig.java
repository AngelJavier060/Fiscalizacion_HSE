package com.fiscalizacionhse.config;

import com.fiscalizacionhse.repository.UsuarioRepository;
import com.fiscalizacionhse.security.JwtAuthenticationFilter;
import com.fiscalizacionhse.security.ModuloAccessFilter;
import com.fiscalizacionhse.service.PermisoService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Auth endpoints - públicos
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/ia/salud").permitAll()

                // Super Admin only
                .requestMatchers("/api/empresas/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/auditoria/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/permisos/**").hasRole("SUPER_ADMIN")

                // Admin Empresa (y Super Admin)
                .requestMatchers("/api/usuarios/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN_EMPRESA")

                // Documentos - Admin Empresa puede CRUD, Usuario solo lectura
                .requestMatchers(HttpMethod.GET, "/api/documentos/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN_EMPRESA", "USUARIO")
                .requestMatchers("/api/documentos/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN_EMPRESA")

                // Puntos Clave
                .requestMatchers(HttpMethod.GET, "/api/puntos-clave/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN_EMPRESA", "USUARIO")
                .requestMatchers("/api/puntos-clave/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN_EMPRESA")

                // Recordatorios - Admin Empresa CRUD, Usuario lectura
                .requestMatchers(HttpMethod.GET, "/api/recordatorios/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN_EMPRESA", "USUARIO")
                .requestMatchers("/api/recordatorios/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN_EMPRESA")

                // Notificaciones - todos los autenticados
                .requestMatchers("/api/notificaciones/**").authenticated()

                // IA - todos los autenticados pueden consultar
                .requestMatchers("/api/ia/consultar").authenticated()
                .requestMatchers("/api/ia/buscar").authenticated()
                .requestMatchers("/api/ia/buscar-asistido").authenticated()
                .requestMatchers("/api/ia/historial").authenticated()
                .requestMatchers("/api/ia/historial/empresa/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN_EMPRESA")
                .requestMatchers("/api/ia/resumir/**").authenticated()
                .requestMatchers("/api/ia/indexar/**")
                    .hasAnyRole("SUPER_ADMIN", "ADMIN_EMPRESA")

                // Cualquier autenticado
                .requestMatchers("/api/me/**").authenticated()

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class)
            // Tras autenticar (JWT), valida el acceso por módulo
            .addFilterAfter(
                new ModuloAccessFilter(usuarioRepository, permisoService),
                JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
