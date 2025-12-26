package com.coffee.blending.web;

import com.coffee.blending.domain.BlendingResult;
import com.coffee.blending.service.BlendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/optimize")
@RequiredArgsConstructor
public class BlendingController {

    private final BlendingService blendingService;

    @PostMapping
    public ResponseEntity<BlendingResult> optimize(@RequestBody BlendingRequest request) {
        BlendingResult result = blendingService.optimizeBlend(
                request.getBatches(), 
                request.getTarget(), 
                request.getParams(),
                request.getAlgorithm()
        );
        return ResponseEntity.ok(result);
    }
}
