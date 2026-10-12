package repository;

import model.FileMetadata;
import model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    
    List<FileMetadata> findByOwnerAndIsDirectory(User owner, Boolean isDirectory);
    
    List<FileMetadata> findByOwnerAndParentId(User owner, Long parentId);
    
    Optional<FileMetadata> findByOwnerAndFilePath(User owner, String filePath);
    Optional<FileMetadata> findByOwnerAndFilePathAndFilename(User owner, String filePath, String filename);
    Optional<FileMetadata> findByFilePathAndFilename(String filePath, String filename);
    List<FileMetadata> findByFileHash(String fileHash);
    
    @Query("SELECT f FROM FileMetadata f WHERE f.owner = :owner AND f.filePath LIKE :pathPattern")
    List<FileMetadata> findByOwnerAndFilePathLike(@Param("owner") User owner, @Param("pathPattern") String pathPattern);
    
    @Query("SELECT f FROM FileMetadata f WHERE f.owner = :owner AND f.filename LIKE :namePattern")
    List<FileMetadata> findByOwnerAndFilenameLike(@Param("owner") User owner, @Param("namePattern") String namePattern);
    
    @Query("SELECT SUM(f.fileSize) FROM FileMetadata f WHERE f.owner = :owner")
    Long getTotalStorageUsedByUser(@Param("owner") User owner);
    
    List<FileMetadata> findByFilenameAndOwner(String filename, User owner);
    
    List<FileMetadata> findByOwner(User owner);
}

