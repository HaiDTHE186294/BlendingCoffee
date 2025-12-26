package com.coffee.blending.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OptimizerParams {
    // Tolerances
    private double priceTolerance;  // Absolute tolerance in VND
    private double flavorTolerance; // Absolute tolerance in sensory score
    
    // Penalties (Cost in Objective Function)
    private double flavorPenaltyPerUnit;
    private double expiryPenaltyPerDay;
    
    // Weights for sensory attributes (to prioritize masking effects e.g. Bitter > Sweet)
    // [Acid, Bitter, Sweet, Caffeine]
    private double weightAcid;
    private double weightBitter;
    private double weightSweet;
    private double weightCaffeine;
    
    // Advanced solver settings
    private double solverTimeoutSec;
    
    // --- MARKET PROFILES (VIETNAMESE CONTEXT 2024-2025) ---
    // Base Price Reference: Robusta ~120k-140k/kg

    /**
     * MASS PROFILE (Phân khúc Bình dân/Giá rẻ)
     * - Ưu tiên giá rẻ nhất có thể.
     * - Chấp nhận lệch vị cao (Flavor Tolerance lớn).
     * - Phạt hết hạn cao (Phải đẩy hàng tồn cũ, FIFO).
     */
    public static OptimizerParams forMassMarket() {
        return OptimizerParams.builder()
                .priceTolerance(1000) // Chặn giá chặt, chỉ cho phép lệch rất nhỏ
                .flavorTolerance(1.5) // Cho phép lệch tới 1.5 điểm vị
                .flavorPenaltyPerUnit(5000) // Phạt nhẹ khi lệch vị (5k VND/điểm) (~4% giá)
                .expiryPenaltyPerDay(200)   // Phạt nặng hàng mới (200đ/ngày) -> Ưu tiên hàng cũ
                .weightAcid(0.5).weightBitter(1.5).weightSweet(0.5).weightCaffeine(1.0) // Chú trọng đắng & caf
                .solverTimeoutSec(5.0)
                .build();
    }

    /**
     * BALANCED PROFILE (Phân khúc Phổ thông/Mainstream)
     * - Cân bằng giữa giá và vị.
     * - Profile chuẩn cho quán cà phê tầm trung.
     */
    public static OptimizerParams forBalancedMarket() {
        return OptimizerParams.builder()
                .priceTolerance(5000)   // Cho phép lệch 5k để tìm vị ngon
                .flavorTolerance(0.5)   // Dung sai vị chuẩn (0.5 điểm)
                .flavorPenaltyPerUnit(20000) // Phạt trung bình (20k VND/điểm) (~15% giá)
                .expiryPenaltyPerDay(100)    // Phạt vừa phải (100đ/ngày)
                .weightAcid(1.0).weightBitter(2.0).weightSweet(1.0).weightCaffeine(1.0)
                .solverTimeoutSec(5.0)
                .build();
    }

    /**
     * SPECIALTY PROFILE (Phân khúc Cao cấp/Signature)
     * - Vị là quan trọng nhất (Flavor Tolerance cực nhỏ).
     * - Giá có thể cao hơn (Price Tolerance lớn).
     * - Chấp nhận dùng hàng mới (Expiry Penalty thấp).
     */
    public static OptimizerParams forSpecialtyMarket() {
        return OptimizerParams.builder()
                .priceTolerance(20000)  // Cho phép lệch tới 20k để đạt đỉnh cao hương vị
                .flavorTolerance(0.2)   // Khắt khe, chỉ lệch 0.2 điểm
                .flavorPenaltyPerUnit(100000) // Phạt cực nặng nếu sai vị (100k VND/điểm)
                .expiryPenaltyPerDay(20)      // Phạt rất thấp, sẵn sàng dùng hàng mới nhất
                .weightAcid(2.0).weightBitter(1.0).weightSweet(2.0).weightCaffeine(0.5) // Ưu tiên Acid/Sweet (Arabica notes)
                .solverTimeoutSec(10.0) // Cho solver nghĩ lâu hơn
                .build();
    }

    public static OptimizerParams defaults() {
        return forBalancedMarket();
    }
}
