package com.fiscalizacionhse.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ia_embeddings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmbeddingDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    @Column(name = "chunk_order", nullable = false)
    @Builder.Default
    private Integer chunkOrder = 0;

    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "token_count")
    @Builder.Default
    private Integer tokenCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private Documento documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
