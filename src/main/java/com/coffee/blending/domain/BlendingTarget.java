package com.coffee.blending.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlendingTarget {
    // Mode: PRICE_OPTIMIZED, QUALITY_OPTIMIZED, BALANCED
    public enum OptimizationMode {
        PRICE_OPTIMIZED,
        QUALITY_OPTIMIZED,
        BALANCED
    }

    private OptimizationMode mode;
    
    // Target price (VND/kg)
    private double targetPrice;
    
    // Target sensory profile
    private double targetAcid;
    private double targetBitter;
    private double targetSweet;
    private double targetCaffeine;
    
    // Total output weight required (kg)
    private double totalOutputKg;
    
    // Constraints
    private int maxBatchTypes; // Max number of different batches to use
    private double minRatio;   // Minimum percentage for a selected batch (e.g., 0.05 for 5%)
}
