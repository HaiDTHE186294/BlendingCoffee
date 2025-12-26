package com.coffee.blending.engine;

import com.coffee.blending.domain.BlendingResult;
import com.coffee.blending.domain.BlendingTarget;
import com.coffee.blending.domain.CoffeeBatch;
import com.coffee.blending.domain.OptimizerParams;

import java.util.List;

public interface BlendingOptimizer {
    BlendingResult optimize(List<CoffeeBatch> batches, BlendingTarget target, OptimizerParams params);
}
