package model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "file_version_chunks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"version_id", "chunk_id", "chunk_index"}))
public class FileVersionChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id")
    private FileVersion version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chunk_id")
    private FileChunk chunk;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;
}