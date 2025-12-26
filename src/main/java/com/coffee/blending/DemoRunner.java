package com.coffee.blending;

import com.coffee.blending.domain.BlendingResult;
import com.coffee.blending.domain.BlendingTarget;
import com.coffee.blending.domain.CoffeeBatch;
import com.coffee.blending.domain.OptimizerParams;
import com.coffee.blending.service.BlendingService;
import com.coffee.blending.engine.GoogleOrToolsOptimizer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DemoRunner implements CommandLineRunner {

    private final BlendingService service;

    public DemoRunner(BlendingService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== STARTING COFFEE BLENDING DEMO (VIETNAMESE MARKET CONTEXT) ===");

        // 1. Setup Batches (Data from Spec)
        // Robusta 2024-2025: ~120k-130k
        CoffeeBatch b1 = CoffeeBatch.builder()
                .id("B01_ROB_OLD")
                .name("Robusta Dak Lak (Old Crop)")
                .price(115000)
                .acid(4.0).bitter(8.0).sweet(3.0).caffeine(2.5)
                .availableStock(1000)
                .daysToExpiry(30) // Old stock
                .build();

        CoffeeBatch b2 = CoffeeBatch.builder()
                .id("B02_ROB_NEW")
                .name("Robusta Dak Lak (New Crop)")
                .price(135000) // High price
                .acid(4.5).bitter(7.5).sweet(4.0).caffeine(2.4)
                .availableStock(5000)
                .daysToExpiry(300) // New stock
                .build();

        CoffeeBatch b3 = CoffeeBatch.builder()
                .id("B03_ARA_DL")
                .name("Arabica Cau Dat")
                .price(220000)
                .acid(8.0).bitter(3.0).sweet(7.0).caffeine(1.2)
                .availableStock(500)
                .daysToExpiry(200)
                .build();

        CoffeeBatch b4 = CoffeeBatch.builder()
                .id("B04_CULI")
                .name("Culi Robusta")
                .price(140000)
                .acid(5.0).bitter(9.0).sweet(3.5).caffeine(3.0)
                .availableStock(800)
                .daysToExpiry(150)
                .build();

        List<CoffeeBatch> batches = Arrays.asList(b1, b2, b3, b4);

        // 2. Setup Target (Standard Pour Over Blend)
        // Target: Balanced Acid/Bitter, decent Sweet
        BlendingTarget target = BlendingTarget.builder()
                .mode(BlendingTarget.OptimizationMode.BALANCED)
                .targetPrice(160000) // Trying to keep cost under 160k
                .targetAcid(5.5)
                .targetBitter(6.0)
                .targetSweet(5.0)
                .targetCaffeine(2.0)
                .totalOutputKg(100)
                .minRatio(0.05) // 5% min if selected
                .maxBatchTypes(3)
                .build();

        // 3. Setup Params (Defaults with slight tweak)
        OptimizerParams params = OptimizerParams.defaults();
        params.setExpiryPenaltyPerDay(100); // Encourage using old stock strongly

        // 4. Run Optimization
        try {
            BlendingResult result = service.optimizeBlend(batches, target, params, "DEFAULT");

            System.out.println("\nOptimization Result: " + result.getStatus());
            System.out.println("Feasible: " + result.isFeasible());
            System.out.println("Objective Value: " + result.getObjectiveValue());
            System.out.println("Computation Time: " + result.getComputationTimeMs() + " ms");
            
            System.out.println("\n--- RECIPE ---");
            result.getComposition().forEach((id, pct) -> {
                System.out.printf("Batch %s: %.2f%% (%.2f kg)\n", id, pct * 100, result.getWeightDistribution().get(id));
            });
            
            System.out.printf("\n--- PREDICTED METRICS vs TARGET ---\n");
            System.out.printf("Price:   %,.0f (Target: %,.0f)\n", result.getPredictedPrice(), target.getTargetPrice());
            System.out.printf("Acid:    %.2f (Target: %.2f)\n", result.getPredictedAcid(), target.getTargetAcid());
            System.out.printf("Bitter:  %.2f (Target: %.2f)\n", result.getPredictedBitter(), target.getTargetBitter());
            System.out.printf("Sweet:   %.2f (Target: %.2f)\n", result.getPredictedSweet(), target.getTargetSweet());
            System.out.printf("Caffeine: %.2f (Target: %.2f)\n", result.getPredictedCaffeine(), target.getTargetCaffeine());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
