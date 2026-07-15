package com.wedjan.api.media;

import com.wedjan.api.config.JwtAuthFilter;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/presign")
    public ResponseEntity<MediaDtos.PresignResponse> presign(
            @Valid @RequestBody MediaDtos.PresignRequest request) {
        MediaDtos.PresignResponse response =
                mediaService.presign(JwtAuthFilter.currentAccountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{mediaId}/complete")
    public MediaDtos.MediaAssetDto complete(@PathVariable UUID mediaId) {
        return mediaService.complete(JwtAuthFilter.currentAccountId(), mediaId);
    }
}
