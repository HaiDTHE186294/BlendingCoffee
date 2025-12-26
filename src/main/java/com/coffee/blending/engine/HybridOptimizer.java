package com.coffee.blending.engine;

import com.coffee.blending.domain.*; // Giả định package chứa DTO
import com.google.ortools.Loader;
import com.google.ortools.linearsolver.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HYBRID OPTIMIZER ENGINE
 * Kết hợp giữa tốc độ (Procedural optimization) và kiến trúc sạch (OOP).
 * Tính năng: MILP Solver, FEFO Logic, Hybrid Hard/Soft Constraints.
 */
@Slf4j
@Component
public class HybridOptimizer implements BlendingOptimizer {

    static {
        // Load thư viện C++ native của Google OR-Tools
        Loader.loadNativeLibraries();
    }

    private static final double BIG_M = 1e9; // Số dương vô cùng cho logic Big-M

    @Override
    public BlendingResult optimize(List<CoffeeBatch> allBatches, BlendingTarget target, OptimizerParams params) {
        long startTime = System.currentTimeMillis();
        
        // 1. Initial Attempt
        // Auto-tune logic is now tracking inside solveInternal or passed via activeParams? 
        // We will move Auto-tuning out here to keep control, or keep it inside.
        // Let's decide: Auto-tuning is base. limit is base.
        
        // Clone params to allow modification during retries
        OptimizerParams currentParams = params.toBuilder().build(); 
        
        // Base Auto-tuning (Profile Selection) happens inside solveInternal based on Mode if params are defaults.
        // But to relax correctly, we needs explicit params. 
        // So we might need to "Resolve" the profile first if it's default.
        if (isDefault(currentParams)) {
             switch (target.getMode()) {
                case PRICE_OPTIMIZED -> currentParams = OptimizerParams.forMassMarket();
                case QUALITY_OPTIMIZED -> currentParams = OptimizerParams.forSpecialtyMarket();
                case BALANCED -> currentParams = OptimizerParams.forBalancedMarket();
            }
        }

        BlendingResult result = null;
        StringBuilder trace = new StringBuilder("Start: Standard Constraints. ");
        int retry = 0;
        final int MAX_RETRIES = 3;

        while (retry <= MAX_RETRIES) {
            result = solveInternal(allBatches, target, currentParams);
            
            // Check Feasibility & Price Constraint Quality
            boolean priceTooHigh = (target.getTargetPrice() > 0) && (result.getPredictedPrice() > target.getTargetPrice() * 1.1);
            
            if (result.isFeasible() && !priceTooHigh) {
                // Success!
                break;
            }
            
            // If failed or poor result -> Trigger Relaxation
            if (retry < MAX_RETRIES) {
                retry++;
                trace.append("\nRetry #").append(retry).append(": ");
                currentParams = relaxParams(currentParams, target.getMode(), retry, trace);
                log.info("Smart Retry #{}: {}", retry, trace.toString());
            } else {
                trace.append("\nFailed after max retries.");
                break; // Prevent infinite loop
            }
        }
        
        result.setRetryCount(retry);
        result.setRelaxationTrace(trace.toString());
        result.setComputationTimeMs(System.currentTimeMillis() - startTime);
        
        if (!result.isFeasible()) {
             result.setStatus(result.getStatus() + " (Relaxed " + retry + " times)");
        }
        
        return result;
    }

    private boolean isDefault(OptimizerParams p) {
        // Simple check if it matches defaults or is null/empty. 
        // For safety, let's assume if it came from the controller as 'defaults', we treat it as such.
        // But here we can just assume true if we want to enforce profiles.
        // Actually, the previous code did: activeParams = params; switch(...) override.
        // So we should respect that logic.
        return true; 
    }
    
    /**
     * CORE SOLVER LOGIC (Private)
     */
    private BlendingResult solveInternal(List<CoffeeBatch> allBatches, BlendingTarget target, OptimizerParams params) {
        long startTime = System.currentTimeMillis();
        // ---------------------------------------------------------
        // BƯỚC 1: PRE-OPTIMIZATION (LỌC DỮ LIỆU)
        // ---------------------------------------------------------
        List<CoffeeBatch> batches = allBatches.stream()
                .filter(b -> b.getAvailableStock() > 0.1)
                .collect(Collectors.toList());

        if (batches.isEmpty()) {
            return BlendingResult.builder().feasible(false).status("OUT_OF_STOCK").build();
        }

        // Khởi tạo Solver (SCIP là solver tốt nhất cho bài toán Mixed-Integer)
        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            log.error("CRITICAL: SCIP Solver not found.");
            return BlendingResult.builder().feasible(false).status("SOLVER_NOT_FOUND").build();
        }
        
        // Giới hạn thời gian (Tránh treo hệ thống)
        solver.setTimeLimit((long) (params.getSolverTimeoutSec() * 1000));

        int n = batches.size();
        MPVariable[] x = new MPVariable[n]; // Biến liên tục: Tỷ lệ % (0.0 - 1.0)
        MPVariable[] y = new MPVariable[n]; // Biến nhị phân: Chọn hay không (0/1)

        // ---------------------------------------------------------
        // BƯỚC 2: KHỞI TẠO BIẾN & RÀNG BUỘC KHO (BOUNDS)
        // ---------------------------------------------------------
        for (int i = 0; i < n; i++) {
            CoffeeBatch batch = batches.get(i);
            
            // Tối ưu hóa: Thay vì thêm 1 phương trình ràng buộc (x * Total <= Stock),
            // ta set luôn cận trên (UpperBound) cho biến x. Solver chạy nhanh hơn nhiều.
            double maxPct = Math.min(1.0, batch.getAvailableStock() / target.getTotalOutputKg());
            
            x[i] = solver.makeNumVar(0.0, maxPct, "x_" + batch.getId());
            y[i] = solver.makeIntVar(0, 1, "y_" + batch.getId());
        }

        // ---------------------------------------------------------
        // BƯỚC 3: RÀNG BUỘC LOGIC (LOGICAL CONSTRAINTS)
        // ---------------------------------------------------------
        
        // 3.1. Tổng tỷ lệ phải bằng 100%
        MPConstraint sumCt = solver.makeConstraint(1.0, 1.0, "sum_must_be_1");
        
        // 3.2. Giới hạn số loại hạt (Cardinality)
        MPConstraint typeCt = solver.makeConstraint(0, target.getMaxBatchTypes(), "max_types");

        for (int i = 0; i < n; i++) {
            sumCt.setCoefficient(x[i], 1.0);
            typeCt.setCoefficient(y[i], 1.0);

            // 3.3. Kỹ thuật Big-M: Liên kết x và y
            // Nếu y=0 (không chọn) -> x phải = 0.
            // Phương trình: x[i] - y[i] <= 0
            MPConstraint linkUp = solver.makeConstraint(-MPSolver.infinity(), 0);
            linkUp.setCoefficient(x[i], 1); 
            linkUp.setCoefficient(y[i], -1);

            // 3.4. Tỷ lệ tối thiểu (Min Ratio)
            // Nếu y=1 (chọn) -> x >= minRatio
            // Phương trình: x[i] - minRatio * y[i] >= 0
            if (target.getMinRatio() > 0) {
                MPConstraint linkLow = solver.makeConstraint(0, MPSolver.infinity());
                linkLow.setCoefficient(x[i], 1); 
                linkLow.setCoefficient(y[i], -target.getMinRatio());
            }
        }

        // ---------------------------------------------------------
        // BƯỚC 4: HYBRID FLAVOR CONSTRAINTS
        // (Kết hợp Ràng buộc Mềm tính Penalty & Ràng buộc Cứng chặn sai số)
        // ---------------------------------------------------------
        
        // --- 4.0. AUTO-TUNING PARAMS BASED ON MARKET PROFILE ---
        // Note: In Smart Retry, 'params' is already the active/relaxed param set passed from optimize()
        OptimizerParams activeParams = params; 
        
        // --- 4.1 PRICE CONSTRAINT (HARD) ---
        
        // --- 4.1 PRICE CONSTRAINT (HARD) ---
        // Constraint: Sum(x[i] * price[i]) <= TargetPrice + Tolerance
        // Ràng buộc này đảm bảo giá blend không vượt quá khả năng chi trả của phân khúc
        if (target.getTargetPrice() > 0) {
            double maxPrice = target.getTargetPrice() + activeParams.getPriceTolerance();
            MPConstraint priceCt = solver.makeConstraint(0, maxPrice, "price_limit");
            for (int i = 0; i < n; i++) {
                priceCt.setCoefficient(x[i], batches.get(i).getPrice());
            }
        }

        // Tạo các biến bù (Slack Variables) cho hàm mục tiêu
        MPVariable dAcidP = solver.makeNumVar(0, MPSolver.infinity(), "dAcid+");
        MPVariable dAcidM = solver.makeNumVar(0, MPSolver.infinity(), "dAcid-");
        
        MPVariable dBitterP = solver.makeNumVar(0, MPSolver.infinity(), "dBitter+");
        MPVariable dBitterM = solver.makeNumVar(0, MPSolver.infinity(), "dBitter-");
        
        MPVariable dSweetP = solver.makeNumVar(0, MPSolver.infinity(), "dSweet+");
        MPVariable dSweetM = solver.makeNumVar(0, MPSolver.infinity(), "dSweet-");
        
        MPVariable dCafP = solver.makeNumVar(0, MPSolver.infinity(), "dCaf+");
        MPVariable dCafM = solver.makeNumVar(0, MPSolver.infinity(), "dCaf-");

        // Logic Hybrid:
        // - PRICE_OPTIMIZED: Cần Hard Bounds (chặn sai số) để không bị lệch vị quá đà vì ham rẻ.
        // - QUALITY_OPTIMIZED: Thả lỏng Hard Bounds, chỉ dùng Soft Penalty để tìm vị ngon nhất.
        boolean useHardBounds = (target.getMode() == BlendingTarget.OptimizationMode.PRICE_OPTIMIZED);
        double hardTol = activeParams.getFlavorTolerance(); // Lấy từ Profile đã Tune

        // Thêm ràng buộc cho từng thuộc tính (Chỉ thêm nếu Target >= 0)
        if (target.getTargetAcid() >= 0) {
            addHybridConstraint(solver, x, batches, dAcidP, dAcidM, target.getTargetAcid(), 
                                useHardBounds ? hardTol : -1, b -> b.getAcid());
        }
        
        if (target.getTargetBitter() >= 0) {
            addHybridConstraint(solver, x, batches, dBitterP, dBitterM, target.getTargetBitter(), 
                                useHardBounds ? hardTol : -1, b -> b.getBitter());
        }
        
        if (target.getTargetSweet() >= 0) {
            addHybridConstraint(solver, x, batches, dSweetP, dSweetM, target.getTargetSweet(), 
                                useHardBounds ? hardTol : -1, b -> b.getSweet());
        }

        // Caffeine
        if (target.getTargetCaffeine() >= 0) {
            addHybridConstraint(solver, x, batches, dCafP, dCafM, target.getTargetCaffeine(), 
                                0.5, b -> b.getCaffeine());
        }

        // ---------------------------------------------------------
        // BƯỚC 5: HÀM MỤC TIÊU (OBJECTIVE FUNCTION)
        // Minimize: Giá + (Ngày hết hạn * ShadowCost) + (Lệch Vị * Penalty)
        // ---------------------------------------------------------
        MPObjective obj = solver.objective();
        obj.setMinimization();

        // SCALING: Divide all costs by 1000 to improve numerical stability for SCIP
        double scale = 0.001;

        // 5.1. Thành phần Kinh tế & Kho vận (Cost + FEFO)
        double expiryPenalty = activeParams.getExpiryPenaltyPerDay(); 
        
        for (int i = 0; i < n; i++) {
            // Shadow Cost calculation
            // Expiry Penalty: Phạt hàng 'MỚI' (DaysToExpiry cao) -> Solver thích hàng 'CŨ' (DaysToExpiry thấp)
            // MASS: Penalty cao -> Hàng mới đắt đỏ ảo -> Solver chọn hàng cũ.
            // SPECIALTY: Penalty thấp -> Hàng mới rẻ hơn (về shadow cost) -> Solver thoải mái chọn.
            double shadowCost = batches.get(i).getDaysToExpiry() * expiryPenalty;
            
            // Scaled coefficient
            double totalCost = batches.get(i).getPrice() + shadowCost;
            obj.setCoefficient(x[i], totalCost * scale);
        }

        // 5.2. Thành phần Hương vị (Flavor Penalty)
        // Công thức: (Lệch Dương + Lệch Âm) * Trọng Số * Hệ Số Phạt
        // SPECIALTY: Penalty cực cao -> Ép sai số về 0.
        // TRỌNG SỐ: Specialty chuộng Acid/Sweet, Mass chuộng Bitter/Caffeine (cho cafe đá).
        double fp = activeParams.getFlavorPenaltyPerUnit(); 
        
        if (target.getTargetAcid() >= 0)
            setPenaltyCoeff(obj, dAcidP, dAcidM, fp * activeParams.getWeightAcid() * scale);
            
        if (target.getTargetBitter() >= 0)
            setPenaltyCoeff(obj, dBitterP, dBitterM, fp * activeParams.getWeightBitter() * scale);
            
        if (target.getTargetSweet() >= 0)
            setPenaltyCoeff(obj, dSweetP, dSweetM, fp * activeParams.getWeightSweet() * scale);
            
        if (target.getTargetCaffeine() >= 0)
            setPenaltyCoeff(obj, dCafP, dCafM, fp * activeParams.getWeightCaffeine() * scale);

        // ---------------------------------------------------------
        // BƯỚC 6: GIẢI & DỰNG KẾT QUẢ
        // ---------------------------------------------------------
        final MPSolver.ResultStatus status = solver.solve();

        return buildResult(status, x, batches, target, obj.value(), System.currentTimeMillis() - startTime);
    }

    // =================================================================
    // HELPER METHODS (PRIVATE)
    // =================================================================

    private interface AttributeExtractor { double get(CoffeeBatch b); }

    /**
     * Thêm ràng buộc lai (Hybrid Constraint).
     * 1. Tạo phương trình cân bằng để tính biến Slack (dPlus, dMinus).
     * 2. Nếu hardTolerance > 0, thiết lập cận trên cho biến Slack để chặn sai số.
     */
    private void addHybridConstraint(MPSolver solver, MPVariable[] x, List<CoffeeBatch> batches,
                                     MPVariable dPlus, MPVariable dMinus, double targetVal,
                                     double hardTolerance, AttributeExtractor extractor) {
        
        // Phương trình: Sum(x[i] * Attribute[i]) - dPlus + dMinus = Target
        MPConstraint balanceCt = solver.makeConstraint(targetVal, targetVal);
        for (int i = 0; i < x.length; i++) {
            balanceCt.setCoefficient(x[i], extractor.get(batches.get(i)));
        }
        balanceCt.setCoefficient(dPlus, -1.0);
        balanceCt.setCoefficient(dMinus, 1.0);

        // Hard Bounds: Chặn sai số nếu cần thiết
        // Nếu hardTolerance = 1.0, nghĩa là |Actual - Target| không được quá 1.0
        if (hardTolerance > 0) {
            dPlus.setBounds(0, hardTolerance);
            dMinus.setBounds(0, hardTolerance);
        }
    }

    private void setPenaltyCoeff(MPObjective obj, MPVariable p, MPVariable m, double weight) {
        obj.setCoefficient(p, weight);
        obj.setCoefficient(m, weight);
    }

    private BlendingResult buildResult(MPSolver.ResultStatus status, MPVariable[] x, 
                                       List<CoffeeBatch> batches, BlendingTarget target, 
                                       double objValue, long duration) {
        
        BlendingResult result = new BlendingResult();
        result.setComputationTimeMs(duration);
        result.setObjectiveValue(objValue);
        result.setStatus(status.name());

        if (status == MPSolver.ResultStatus.OPTIMAL || status == MPSolver.ResultStatus.FEASIBLE) {
            result.setFeasible(true);
            
            Map<String, Double> composition = new HashMap<>();
            Map<String, Double> weightDist = new HashMap<>();
            double finalPrice = 0, finalAcid = 0, finalBitter = 0, finalSweet = 0, finalCaf = 0;

            for (int i = 0; i < x.length; i++) {
                double ratio = x[i].solutionValue();
                
                // Lọc bỏ các số quá nhỏ (nhiễu số học)
                if (ratio > 0.001) {
                    CoffeeBatch b = batches.get(i);
                    composition.put(b.getId(), ratio);
                    weightDist.put(b.getId(), ratio * target.getTotalOutputKg());
                    
                    finalPrice += ratio * b.getPrice();
                    finalAcid += ratio * b.getAcid();
                    finalBitter += ratio * b.getBitter();
                    finalSweet += ratio * b.getSweet();
                    finalCaf += ratio * b.getCaffeine();
                }
            }
            
            result.setComposition(composition);
            result.setWeightDistribution(weightDist);
            result.setPredictedPrice(finalPrice);
            result.setPredictedAcid(finalAcid);
            result.setPredictedBitter(finalBitter);
            result.setPredictedSweet(finalSweet);
            result.setPredictedCaffeine(finalCaf);
            
            // Tính điểm tương đồng (Similarity Score - %)
            // Công thức đơn giản: 100 - (Tổng độ lệch / Tổng Target * 100)
            double totalDev = Math.abs(finalAcid - target.getTargetAcid()) 
                            + Math.abs(finalBitter - target.getTargetBitter()) 
                            + Math.abs(finalSweet - target.getTargetSweet());
            double totalTarget = target.getTargetAcid() + target.getTargetBitter() + target.getTargetSweet();
            result.setSimilarityScore(Math.max(0, 100.0 - (totalDev / totalTarget * 100.0)));
            
        } else {
            result.setFeasible(false);
        }
        
        return result;
    }

    /**
     * LOGIC THƯƠNG LƯỢNG RÀNG BUỘC (SMART RELAXATION)
     * Tư duy kinh tế: "Nếu không đạt được mục tiêu lý tưởng, hãy hy sinh cái ít quan trọng nhất".
     */
    private OptimizerParams relaxParams(OptimizerParams current, BlendingTarget.OptimizationMode mode, int attempt, StringBuilder trace) {
        OptimizerParams.OptimizerParamsBuilder b = current.toBuilder();
        
        // Strategy depends on Mode
        if (mode == BlendingTarget.OptimizationMode.PRICE_OPTIMIZED) {
            // MASS MARKET: Ưu tiên GIÁ -> Hy sinh VỊ
            switch (attempt) {
                case 1 -> { 
                    trace.append("Relax Flavor Tol (+1.0).");
                    b.flavorTolerance(current.getFlavorTolerance() + 1.0); 
                }
                case 2 -> {
                    trace.append("Relax Price Tol (+5%).");
                    b.priceTolerance(current.getPriceTolerance() * 1.05); // Nới nhẹ giá
                }
                case 3 -> {
                    trace.append("Reduce Flavor Importance (-20%).");
                    b.flavorPenaltyPerUnit(current.getFlavorPenaltyPerUnit() * 0.8);
                }
            }
        } else if (mode == BlendingTarget.OptimizationMode.QUALITY_OPTIMIZED) {
            // SPECIALTY: Ưu tiên VỊ -> Hy sinh GIÁ
            switch (attempt) {
                case 1 -> {
                    trace.append("Relax Price Tol (+10%)."); 
                    b.priceTolerance(current.getPriceTolerance() * 1.10); // Cho phép đắt hơn
                }
                case 2 -> {
                    trace.append("Relax Flavor Tol (+0.2).");
                    b.flavorTolerance(current.getFlavorTolerance() + 0.2); // Nới cực nhẹ vị
                }
                case 3 -> {
                    trace.append("Relax Price & Expiry.");
                    b.priceTolerance(current.getPriceTolerance() * 1.20);
                    b.expiryPenaltyPerDay(current.getExpiryPenaltyPerDay() * 0.5); // Cho phép hàng mới hơn nữa
                }
            }
        } else {
            // BALANCED: Hy sinh đều
            switch (attempt) {
                case 1 -> {
                    trace.append("Relax Flavor (+0.5).");
                    b.flavorTolerance(current.getFlavorTolerance() + 0.5);
                }
                case 2 -> {
                    trace.append("Relax Price (+5%).");
                    b.priceTolerance(current.getPriceTolerance() * 1.05);
                }
                case 3 -> {
                    trace.append("Relax All Constraints.");
                    b.flavorTolerance(current.getFlavorTolerance() + 0.5);
                    b.priceTolerance(current.getPriceTolerance() * 1.10);
                }
            }
        }
        
        return b.build();
    }
}