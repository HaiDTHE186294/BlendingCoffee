# ☕ Intelligent Coffee Blending Optimization Engine

**CoffeeBlend.AI** is a high-performance optimization system designed to solve the complex problem of coffee blending. It helps manufacturers create the perfect blend recipe (`Composition`) that meets specific sensory profiles (Acid, Bitter, Sweet, Caffeine) while minimizing costs and managing inventory (FEFO).

![Java](https://img.shields.io/badge/Java-17-orange) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green) ![OR-Tools](https://img.shields.io/badge/Solver-Google_OR--Tools-blue) ![License](https://img.shields.io/badge/License-MIT-lightgrey)

## 🚀 Key Features

*   **Hybrid Optimization Engine**: Combines Mixed-Integer Linear Programming (MILP using Google OR-Tools/SCIP) with domain-specific heuristics.
*   **Smart Retry Strategy**: Automatically "negotiates" constraints (Market Profiles) if the target is initially infeasible. It relaxes flavor/price constraints intelligently to find the best possible compromise.
*   **Market Profiles**: Built-in logic for Vietnamese coffee market segments:
    *   **Mass Market**: Price-optimized, FIFO prioritized.
    *   **Balanced**: Trade-off between quality and cost.
    *   **Specialty**: Quality-first, tight flavor tolerances, price relaxing.
*   **Comparison Mode**: Run 3 optimization scenarios simultaneously to visualize trade-offs.
*   **Advanced Inventory Management**: Includes Expiry Penalty logic (FEFO) to prioritize older stock automatically.
*   **Modern Interactive UI**:
    - Dark-themed, glassmorphism design.
    - Interactive Radar & Doughnut charts (Chart.js).
    - Real-time comparison tools.
    - Advanced configuration for R&D.

## 🛠️ Technology Stack

*   **Backend**: Java 17, Spring Boot 3
*   **Solver**: Google OR-Tools (SCIP Backend)
*   **Build Tool**: Gradle 8.12
*   **Frontend**: Vanilla JavaScript (ES6+), HTML5, CSS3 (Variables & Glassmorphism)
*   **Visualizations**: Chart.js

## 📦 Installation & Setup

### Prerequisites
*   JDK 17 or higher installed.
*   Git installed.

### Steps
1.  **Clone the repository**
    ```bash
    git clone https://github.com/HaiDTHE186294/BlendingCoffee.git
    cd BlendingCoffee
    ```

2.  **Build the project**
    ```bash
    # Windows
    ./gradlew clean build

    # Mac/Linux
    ./gradlew clean build
    ```

3.  **Run the application**
    ```bash
    ./gradlew bootRun
    ```
    The server will start at `http://localhost:8080`.

## 📖 Usage Guide

1.  **Open the Web Interface**: Navigate to `http://localhost:8080`.
2.  **Manage Inventory**: Add/Edit/Removing coffee batches in the left panel.
3.  **Set Targets**:
    *   Choose a mode (Balanced, Price, Quality).
    *   Set Target Price and Sensory Scores (Acid, Bitter, Sweet, Caffeine).
    *   *Tip*: Uncheck a sensory attribute to disable optimization for that specific trait (Cost-saving exploration).
4.  **Optimize**:
    *   Click **"🚀 Chạy Tối Ưu Hóa"** for a single result.
    *   Click **"⚖️ So sánh 3 Chế độ"** to see how Mass vs. Balanced vs. Specialty strategies differ for your current inventory.
5.  **View Results**: Check the smart logs to see if the engine had to "relax" any constraints to achieve the result.

## 🧠 Algorithmic Details (Hybrid Optimizer)

The core logic lies in `HybridOptimizer.java`, which transforms the blending requirement into a MILP problem:

$$
\text{Minimize} \quad Z = \sum (Price_i + ExpiryCost_i) \cdot x_i + \text{FlavorPenalties}
$$

Subject to:
*   $\sum x_i = 100\%$
*   $x_i \le \text{AvailableStock}_i$
*   $| \text{ActualFlavor} - \text{TargetFlavor} | \le \text{Tolerance}$ (Soft/Hard Constraints)
*   $\text{Price} \le \text{TargetPrice} + \text{PriceTolerance}$

## 🤝 Contributing

Contributions are welcome! Please fork the repository and submit a Pull Request.

## 📄 License

This project is licensed under the MIT License.
