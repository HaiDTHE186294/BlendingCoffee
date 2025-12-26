package com.coffee.blending.web;

import com.coffee.blending.domain.BlendingTarget;
import com.coffee.blending.domain.CoffeeBatch;
import com.coffee.blending.domain.OptimizerParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlendingRequest {
    private List<CoffeeBatch> batches;
    private BlendingTarget target;
    private OptimizerParams params;
    private String algorithm; // "DEFAULT" or "HYBRID"
}
