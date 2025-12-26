package com.coffee.blending.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlendingResult {
    private boolean feasible;
    private String status; // "OPTIMAL", "FEASIBLE", "INFEASIBLE"
    
    private Map<String, Double> composition; // Batch ID -> Percentage (0.0 - 1.0)
    private Map<String, Double> weightDistribution; // Batch ID -> Kg
    
    // Predicted properties of the blend
    private double predictedPrice;
    private double predictedAcid;
    private double predictedBitter;
    private double predictedSweet;
    private double predictedCaffeine;
    
    // Quality metric (0-100%)
    private double similarityScore;
    
    // Metrics
    private double objectiveValue;
    private long computationTimeMs;
    
    // Smart Retry Info
    private int retryCount;
    private String relaxationTrace;
}
