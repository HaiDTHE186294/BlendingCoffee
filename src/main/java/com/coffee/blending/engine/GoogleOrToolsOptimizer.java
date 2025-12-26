package com.coffee.blending.engine;

import com.coffee.blending.domain.BlendingResult;
import com.coffee.blending.domain.BlendingTarget;
import com.coffee.blending.domain.CoffeeBatch;
import com.coffee.blending.domain.OptimizerParams;
import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GoogleOrToolsOptimizer implements BlendingOptimizer {

    static {
        Loader.loadNativeLibraries();
    }

    @Override
    public BlendingResult optimize(List<CoffeeBatch> batches, BlendingTarget target, OptimizerParams params) {
        long startTime = System.currentTimeMillis();
        
        // 1. Initialize Solver
        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            log.error("Could not create solver SCIP");
            return BlendingResult.builder().feasible(false).status("SOLVER_NOT_FOUND").build();
        }

        int n = batches.size();
        
        // 2. Define Variables
        // x[i]: Percentage of batch i (0.0 to 1.0)
        MPVariable[] x = new MPVariable[n];
        // y[i]: Binary variable, 1 if batch i is used, 0 otherwise
        MPVariable[] y = new MPVariable[n];

        for (int i = 0; i < n; i++) {
            x[i] = solver.makeNumVar(0.0, 1.0, "x_" + i);
            y[i] = solver.makeIntVar(0.0, 1.0, "y_" + i);
        }

        // 3. Constraints

        // C1. Sum of percentages = 1.0
        MPConstraint sumEncoded = solver.makeConstraint(1.0, 1.0, "sum_one");
        for (int i = 0; i < n; i++) {
            sumEncoded.setCoefficient(x[i], 1.0);
        }

        // C2. Link x[i] and y[i] and Min Ratio
        // x[i] <= y[i]  =>  x[i] - y[i] <= 0
        // x[i] >= minRatio * y[i] => x[i] - minRatio * y[i] >= 0
        double minRatio = target.getMinRatio() > 0 ? target.getMinRatio() : 0.0;
        
        for (int i = 0; i < n; i++) {
            // Upper bound link
            MPConstraint linkUp = solver.makeConstraint(-MPSolver.infinity(), 0.0, "link_up_" + i);
            linkUp.setCoefficient(x[i], 1.0);
            linkUp.setCoefficient(y[i], -1.0);

            // Lower bound link (min ratio)
            if (minRatio > 0) {
                MPConstraint linkLow = solver.makeConstraint(0.0, MPSolver.infinity(), "link_low_" + i);
                linkLow.setCoefficient(x[i], 1.0);
                linkLow.setCoefficient(y[i], -minRatio);
            }
        }

        // C3. Max Batch Types
        if (target.getMaxBatchTypes() > 0) {
            MPConstraint maxTypes = solver.makeConstraint(0.0, target.getMaxBatchTypes(), "max_types");
            for (int i = 0; i < n; i++) {
                maxTypes.setCoefficient(y[i], 1.0);
            }
        }

        // C4. Stock Availability
        // x[i] * TotalOutput <= Stock[i]
        // x[i] <= Stock[i] / TotalOutput
        for (int i = 0; i < n; i++) {
            double maxAllowedPct = batches.get(i).getAvailableStock() / target.getTotalOutputKg();
            // If maxAllowedPct > 1, it means stock is sufficient for 100%, so we bound by 1.0
            if (maxAllowedPct < 1.0) {
                x[i].setBounds(0.0, maxAllowedPct);
            }
        }

        // 4. Soft Constraints & Objective
        MPObjective objective = solver.objective();
        
        // Define Goal variables (Price & Flavor) with Slack
        // Flavors: Acid, Bitter, Sweet, Caffeine
        // We calculate the blend property: BlendAttr = sum(x[i] * BatchAttr[i])
        // We want |BlendAttr - TargetAttr| <= Tolerance + Penalty
        
        // Add slack variables for deviation
        MPVariable dPricePlus = solver.makeNumVar(0.0, MPSolver.infinity(), "d_price_plus");
        MPVariable dPriceMinus = solver.makeNumVar(0.0, MPSolver.infinity(), "d_price_minus");
        
        MPVariable dAcidPlus = solver.makeNumVar(0.0, MPSolver.infinity(), "d_acid_plus");
        MPVariable dAcidMinus = solver.makeNumVar(0.0, MPSolver.infinity(), "d_acid_minus");
        
        MPVariable dBitterPlus = solver.makeNumVar(0.0, MPSolver.infinity(), "d_bitter_plus");
        MPVariable dBitterMinus = solver.makeNumVar(0.0, MPSolver.infinity(), "d_bitter_minus");
        
        MPVariable dSweetPlus = solver.makeNumVar(0.0, MPSolver.infinity(), "d_sweet_plus");
        MPVariable dSweetMinus = solver.makeNumVar(0.0, MPSolver.infinity(), "d_sweet_minus");

        MPVariable dCafPlus = solver.makeNumVar(0.0, MPSolver.infinity(), "d_caf_plus");
        MPVariable dCafMinus = solver.makeNumVar(0.0, MPSolver.infinity(), "d_caf_minus");

        // Price Constraint Equation: Sum(x[i]*P[i]) - dP+ + dP- = TargetPrice
        // => Sum(x[i]*P[i]) - dP+ + dP- = T
        MPConstraint priceCons = solver.makeConstraint(target.getTargetPrice(), target.getTargetPrice(), "balance_price");
        for (int i = 0; i < n; i++) {
            priceCons.setCoefficient(x[i], batches.get(i).getPrice());
        }
        priceCons.setCoefficient(dPricePlus, -1.0);
        priceCons.setCoefficient(dPriceMinus, 1.0);

        // Flavor Constraint Equations
        addFlavorConstraint(solver, x, batches, dAcidPlus, dAcidMinus, target.getTargetAcid(), "acid", (b) -> b.getAcid());
        addFlavorConstraint(solver, x, batches, dBitterPlus, dBitterMinus, target.getTargetBitter(), "bitter", (b) -> b.getBitter());
        addFlavorConstraint(solver, x, batches, dSweetPlus, dSweetMinus, target.getTargetSweet(), "sweet", (b) -> b.getSweet());
        addFlavorConstraint(solver, x, batches, dCafPlus, dCafMinus, target.getTargetCaffeine(), "caffeine", (b) -> b.getCaffeine());

        // Objective Function Weights & Coefficients
        // Cost Minimization (Base)
        // minimize Sum(x[i] * Price[i]) -> Direct Cost
        // OR minimize Deviation from TargetPrice
        
        // Based on Mode:
        // PRICE_OPTIMIZED: Minimize (Sum P[i]*x[i]) + Tolerated Deviations
        // QUALITY_OPTIMIZED: Minimize Flavor Deviations (Penalty) + Price Deviation (Penalty)
        
        // Let's unify: Minimize TotalCost
        // TotalCost = (PriceComponent) + (FlavorComponent) + (ExpiryComponent)
        
        // 1. Price Component
        // SCALING: Divide all objective coefficients by 1000
        double scale = 0.001;
        
        boolean minimizeAbsolutePrice = target.getMode() == BlendingTarget.OptimizationMode.PRICE_OPTIMIZED;
        
        if (minimizeAbsolutePrice) {
            for (int i = 0; i < n; i++) {
                objective.setCoefficient(x[i], batches.get(i).getPrice() * scale);
            }
        } else {
            for (int i = 0; i < n; i++) {
                 objective.setCoefficient(x[i], batches.get(i).getPrice() * scale);
            }
        }

        // 2. Flavor Component (Penalty)
        // Cost += (dFlavorPlus + dFlavorMinus) * PenaltyPerUnit * Weight
        double fp = params.getFlavorPenaltyPerUnit();
        objective.setCoefficient(dAcidPlus, fp * params.getWeightAcid() * scale);
        objective.setCoefficient(dAcidMinus, fp * params.getWeightAcid() * scale);
        
        objective.setCoefficient(dBitterPlus, fp * params.getWeightBitter() * scale);
        objective.setCoefficient(dBitterMinus, fp * params.getWeightBitter() * scale);
        
        objective.setCoefficient(dSweetPlus, fp * params.getWeightSweet() * scale);
        objective.setCoefficient(dSweetMinus, fp * params.getWeightSweet() * scale);
        
        objective.setCoefficient(dCafPlus, fp * params.getWeightCaffeine() * scale);
        objective.setCoefficient(dCafMinus, fp * params.getWeightCaffeine() * scale);

        // 3. Expiry Component (Penalty for using fresh beans)
        double ep = params.getExpiryPenaltyPerDay();
        for (int i = 0; i < n; i++) {
            double currentCoef = objective.getCoefficient(x[i]);
            objective.setCoefficient(x[i], currentCoef + (batches.get(i).getDaysToExpiry() * ep * scale));
        }

        objective.setMinimization();

        // Solve
        final MPSolver.ResultStatus status = solver.solve();

        long endTime = System.currentTimeMillis();
        
        BlendingResult result = new BlendingResult();
        result.setComputationTimeMs(endTime - startTime);
        result.setObjectiveValue(objective.value());
        
        if (status == MPSolver.ResultStatus.OPTIMAL || status == MPSolver.ResultStatus.FEASIBLE) {
            result.setFeasible(true);
            result.setStatus(status.name());
            
            Map<String, Double> composition = new HashMap<>();
            Map<String, Double> weights = new HashMap<>();
            
            double finalPrice = 0;
            double finalAcid = 0;
            double finalBitter = 0;
            double finalSweet = 0;
            double finalCaf = 0;

            for (int i = 0; i < n; i++) {
                double val = x[i].solutionValue();
                if (val > 0.0001) { // Threshold for zero
                    composition.put(batches.get(i).getId(), val);
                    weights.put(batches.get(i).getId(), val * target.getTotalOutputKg());
                    
                    finalPrice += val * batches.get(i).getPrice();
                    finalAcid += val * batches.get(i).getAcid();
                    finalBitter += val * batches.get(i).getBitter();
                    finalSweet += val * batches.get(i).getSweet();
                    finalCaf += val * batches.get(i).getCaffeine();
                }
            }
            
            result.setComposition(composition);
            result.setWeightDistribution(weights);
            result.setPredictedPrice(finalPrice);
            result.setPredictedAcid(finalAcid);
            result.setPredictedBitter(finalBitter);
            result.setPredictedSweet(finalSweet);
            result.setPredictedCaffeine(finalCaf);
        } else {
            result.setFeasible(false);
            result.setStatus(status.name());
        }
        
        return result;
    }

    private interface AttributeExtractor {
        double get(CoffeeBatch b);
    }

    private void addFlavorConstraint(MPSolver solver, MPVariable[] x, List<CoffeeBatch> batches,
                                     MPVariable dPlus, MPVariable dMinus, double targetVal,
                                     String name, AttributeExtractor extractor) {
        // Sum(x[i]*Attr[i]) - d+ + d- = Target
        MPConstraint c = solver.makeConstraint(targetVal, targetVal, name + "_balance");
        for (int i = 0; i < x.length; i++) {
            c.setCoefficient(x[i], extractor.get(batches.get(i)));
        }
        c.setCoefficient(dPlus, -1.0);
        c.setCoefficient(dMinus, 1.0);
    }
}
