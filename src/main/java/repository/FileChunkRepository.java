package repository;

import model.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileChunkRepository extends JpaRepository<FileChunk, Long> {
    
    Optional<FileChunk> findByChunkHash(String chunkHash);
    
    List<FileChunk> findByFile_IdOrderByChunkIndex(Long fileId);
    
    boolean existsByChunkHash(String chunkHash);
}

