package controller;

import dto.ChunkDedupInitRequest;
import dto.ChunkDedupInitResponse;
import dto.ChunkUploadRequest;
import dto.FinalizeAssembleRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import service.DedupService;
import service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api/dedup")
@CrossOrigin(origins = "*")
public class DedupController {

    @Autowired
    private DedupService dedupService;

    @Autowired
    private UserService userService;

    @PostMapping("/init")
    public ResponseEntity<ChunkDedupInitResponse> init(@RequestBody ChunkDedupInitRequest req,
                                                       @RequestHeader("Authorization") String token) {
        String clean = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long userId = userService.getUserIdFromToken(clean);
        ChunkDedupInitResponse resp = dedupService.initDedup(req, userId);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/upload-chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(@RequestBody ChunkUploadRequest req,
                                                           @RequestHeader("Authorization") String token) throws Exception {
        String clean = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long userId = userService.getUserIdFromToken(clean);
        Map<String, Object> res = dedupService.uploadChunk(req, userId);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/finalize")
    public ResponseEntity<Map<String, Object>> finalizeAssemble(@RequestBody FinalizeAssembleRequest req,
                                                                @RequestHeader("Authorization") String token) throws Exception {
        String clean = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long userId = userService.getUserIdFromToken(clean);
        Map<String, Object> res = dedupService.finalizeAssemble(req, userId);
        return ResponseEntity.ok(res);
    }
}
