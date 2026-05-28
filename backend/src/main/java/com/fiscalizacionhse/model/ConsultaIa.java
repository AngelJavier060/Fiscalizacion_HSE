package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "ia_consultas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultaIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pregunta;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String respuesta;

    /** JSON almacenado en PostgreSQL como JSONB; el String debe ser JSON válido (p. ej. array de docs). */
    @Column(name = "documentos_referencia")
    @JdbcTypeCode(SqlTypes.JSON)
    private String documentosReferencia;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String tipo = "CONSULTA";

    @Column(length = 10)
    private String feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
