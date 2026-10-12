package repository;

import model.FileShare;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileShareRepository extends JpaRepository<FileShare, Long> {
    
    Optional<FileShare> findByShareToken(String shareToken);
    
    @EntityGraph(attributePaths = {"file", "user", "sharedWithUser"})
    List<FileShare> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"file", "user", "sharedWithUser"})
    List<FileShare> findBySharedWithUserId(Long sharedWithUserId);

    @EntityGraph(attributePaths = {"file", "user", "sharedWithUser"})
    List<FileShare> findByFileId(Long fileId);
    
    @Query("SELECT fs FROM FileShare fs WHERE fs.shareToken = :token AND fs.isActive = true AND (fs.expiresAt IS NULL OR fs.expiresAt > CURRENT_TIMESTAMP)")
    Optional<FileShare> findActiveShareByToken(@Param("token") String shareToken);
}

