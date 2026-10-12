package controller;

import dto.ChunkIndexResponse;
import dto.DeltaApplyRequest;
import dto.DeltaApplyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import service.DeltaSyncService;

@RestController
@RequestMapping("/api/delta")
public class DeltaSyncController {

    @Autowired
    private DeltaSyncService deltaSyncService;

    @GetMapping("/files/{id}/chunks/index")
    public ResponseEntity<ChunkIndexResponse> getIndex(@PathVariable("id") Long fileId,
                                                       @RequestParam(value = "chunkSize", defaultValue = "32768") int chunkSize) throws Exception {
        ChunkIndexResponse resp = deltaSyncService.getChunkIndex(fileId, chunkSize);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/files/{id}/chunks/apply")
    public ResponseEntity<DeltaApplyResponse> apply(@PathVariable("id") Long fileId,
                                                    @RequestBody DeltaApplyRequest request) throws Exception {
        DeltaApplyResponse resp = deltaSyncService.applyDelta(fileId, request);
        return ResponseEntity.ok(resp);
    }
}
