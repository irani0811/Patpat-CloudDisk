package repository;

import model.SyncEvent;
import model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SyncEventRepository extends JpaRepository<SyncEvent, Long> {
    
    List<SyncEvent> findByUserAndIsProcessedOrderByEventTime(User user, Boolean isProcessed);
    
    List<SyncEvent> findByUserAndEventTimeAfterOrderByEventTime(User user, LocalDateTime since);

    List<SyncEvent> findByUserAndIdGreaterThanOrderByEventTime(User user, Long lastId);
    
    @Query("SELECT se FROM SyncEvent se WHERE se.user = :user AND se.isProcessed = false ORDER BY se.eventTime")
    List<SyncEvent> findUnprocessedEventsByUser(@Param("user") User user);
    
    List<SyncEvent> findByUserOrderByEventTime(User user);

    @Query("SELECT se FROM SyncEvent se WHERE se.user = :user ORDER BY se.eventTime DESC")
    List<SyncEvent> findRecentEvents(@Param("user") User user, org.springframework.data.domain.Pageable pageable);
}

