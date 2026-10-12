package repository;

import model.FileVersion;
import model.FileVersionChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileVersionChunkRepository extends JpaRepository<FileVersionChunk, Long> {
    List<FileVersionChunk> findByVersionOrderByChunkIndex(FileVersion version);
}