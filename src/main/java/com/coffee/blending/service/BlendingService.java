package com.coffee.blending.service;

import com.coffee.blending.domain.BlendingResult;
import com.coffee.blending.domain.BlendingTarget;
import com.coffee.blending.domain.CoffeeBatch;
import com.coffee.blending.domain.OptimizerParams;
import com.coffee.blending.engine.BlendingOptimizer;
import com.coffee.blending.engine.GoogleOrToolsOptimizer;
import com.coffee.blending.engine.HybridOptimizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlendingService {

    private final GoogleOrToolsOptimizer googleOptimizer;
    private final HybridOptimizer hybridOptimizer;

    public BlendingResult optimizeBlend(List<CoffeeBatch> batches, BlendingTarget target, OptimizerParams params, String algorithm) {
        // Fallback to defaults if params are missing
        if (params == null) {
            params = OptimizerParams.defaults();
        }
        
        // Basic Validation
        if (batches == null || batches.isEmpty()) {
            throw new IllegalArgumentException("Batch list cannot be empty");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target cannot be null");
        }

        // Algorithm Selection
        BlendingOptimizer optimizer;
        if ("HYBRID".equalsIgnoreCase(algorithm)) {
            optimizer = hybridOptimizer;
        } else {
            optimizer = googleOptimizer;
        }

        return optimizer.optimize(batches, target, params);
    }
}
