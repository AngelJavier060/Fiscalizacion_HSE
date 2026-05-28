package com.fiscalizacionhse.model;

import com.fiscalizacionhse.model.enums.RolUsuario;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RolUsuario rol = RolUsuario.USUARIO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    /** Vigencia de acceso del usuario (null = sin límite) */
    @Column(name = "acceso_desde")
    private LocalDate accesoDesde;

    @Column(name = "acceso_hasta")
    private LocalDate accesoHasta;

    /** Si true, el acceso a módulos se toma de usuario_modulo; si false, hereda del rol */
    @Column(name = "accesos_personalizados", nullable = false)
    @Builder.Default
    private Boolean accesosPersonalizados = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
