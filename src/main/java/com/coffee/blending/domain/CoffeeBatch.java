package com.coffee.blending.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoffeeBatch {
    private String id;
    private String name;
    
    // Price per kg (VND)
    private double price;
    
    // Sensory attributes (0-10 or 0-100 scale, must be consistent)
    private double acid;
    private double bitter;
    private double sweet;
    
    // Caffeine content (e.g., percentage or mg/g)
    private double caffeine;
    
    // Inventory constraints
    private double availableStock; // kg
    
    // Expiry information
    private int daysToExpiry;
}
