// App State
const state = {
    batches: [
        { id: "B1", name: "Robusta Dak Lak", price: 120000, acid: 4, bitter: 8, sweet: 3, caffeine: 2.5, stock: 1000, expiry: 50 },
        { id: "B2", name: "Arabica Cau Dat", price: 220000, acid: 8, bitter: 3, sweet: 7, caffeine: 1.2, stock: 500, expiry: 200 },
        { id: "B3", name: "Culi Special", price: 140000, acid: 5, bitter: 9, sweet: 3.5, caffeine: 3.0, stock: 800, expiry: 100 }
    ],
    target: {
        mode: "BALANCED"
    }
};

let charts = {};

// Init
document.addEventListener("DOMContentLoaded", () => {
    renderBatches();
    setupListeners();
});

function setupListeners() {
    document.getElementById("add-batch-modal-btn").addEventListener("click", () => openModal());
    document.querySelector(".close-modal").addEventListener("click", closeModal);
    document.getElementById("save-batch-btn").addEventListener("click", saveBatchFromModal);
    document.getElementById("optimize-btn").addEventListener("click", runOptimization);
    document.getElementById("compare-btn").addEventListener("click", runComparison); // New
    document.querySelector(".close-modal-compare").addEventListener("click", () => {
        document.getElementById("compare-modal").classList.add("hidden");
    });

    // Tab Logic
    document.querySelectorAll(".tab-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            // Remove active class
            document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
            document.querySelectorAll(".tab-content").forEach(c => c.classList.remove("active"));

            // Add active class
            btn.classList.add("active");
            document.getElementById(btn.dataset.tab).classList.add("active");
        });
    });

    // Close modal on outside click
    window.addEventListener("click", (e) => {
        const modal = document.getElementById("batch-modal");
        if (e.target === modal) closeModal();
    });
}

// === BATCH MANAGEMENT ===

function renderBatches() {
    const list = document.getElementById("batch-list");
    list.innerHTML = "";

    state.batches.forEach((b, index) => {
        const el = document.createElement("div");
        el.className = "batch-card";
        el.onclick = (e) => {
            if (!e.target.classList.contains('delete-btn')) openModal(index);
        };
        el.innerHTML = `
            <div class="batch-header">
                <span>${b.name}</span>
                <span>${parseInt(b.price).toLocaleString()} đ</span>
            </div>
            <div class="batch-details">
                <span>Tồn: ${b.stock}kg</span>
                <span>Date: ${b.expiry} ngày</span>
                <span>A:${b.acid} B:${b.bitter} S:${b.sweet}</span>
            </div>
            <button class="delete-btn" onclick="removeBatch(${index})">✕</button>
        `;
        list.appendChild(el);
    });
}

function openModal(index = null) {
    const modal = document.getElementById("batch-modal");
    const title = document.getElementById("modal-title");
    const indexInput = document.getElementById("modal-index");

    modal.classList.remove("hidden");

    if (index !== null) {
        // Edit Mode
        const b = state.batches[index];
        title.textContent = "Chỉnh sửa Lô hàng";
        indexInput.value = index;

        document.getElementById("modal-name").value = b.name;
        document.getElementById("modal-price").value = b.price;
        document.getElementById("modal-stock").value = b.stock;
        document.getElementById("modal-expiry").value = b.expiry;
        document.getElementById("modal-caf").value = b.caffeine;
        document.getElementById("modal-acid").value = b.acid;
        document.getElementById("modal-bitter").value = b.bitter;
        document.getElementById("modal-sweet").value = b.sweet;
    } else {
        // New Mode
        title.textContent = "Thêm Lô hàng Mới";
        indexInput.value = "-1";

        // Default values
        document.getElementById("modal-name").value = "Lô mới #" + (state.batches.length + 1);
        document.getElementById("modal-price").value = 120000;
        document.getElementById("modal-stock").value = 1000;
        document.getElementById("modal-expiry").value = 90;
        document.getElementById("modal-caf").value = 2.0;
        document.getElementById("modal-acid").value = 5.0;
        document.getElementById("modal-bitter").value = 5.0;
        document.getElementById("modal-sweet").value = 5.0;
    }
}

function closeModal() {
    document.getElementById("batch-modal").classList.add("hidden");
}

function saveBatchFromModal() {
    const index = parseInt(document.getElementById("modal-index").value);

    const batchData = {
        id: index === -1 ? "B" + Date.now() : state.batches[index].id,
        name: document.getElementById("modal-name").value,
        price: parseFloat(document.getElementById("modal-price").value),
        stock: parseFloat(document.getElementById("modal-stock").value),
        expiry: parseInt(document.getElementById("modal-expiry").value),
        caffeine: parseFloat(document.getElementById("modal-caf").value),
        acid: parseFloat(document.getElementById("modal-acid").value),
        bitter: parseFloat(document.getElementById("modal-bitter").value),
        sweet: parseFloat(document.getElementById("modal-sweet").value)
    };

    if (index === -1) {
        state.batches.push(batchData);
    } else {
        state.batches[index] = batchData;
    }

    renderBatches();
    closeModal();
}

window.removeBatch = (index) => {
    state.batches.splice(index, 1);
    renderBatches();
};

// === OPTIMIZATION ===

async function runOptimization() {
    const btn = document.getElementById("optimize-btn");
    btn.textContent = "⏳ Đang tính toán...";
    btn.disabled = true;

    // Collect Input Data
    const requestPayload = {
        batches: state.batches.map(b => ({
            id: b.id,
            name: b.name,
            price: b.price,
            acid: b.acid,
            bitter: b.bitter,
            sweet: b.sweet,
            caffeine: b.caffeine,
            availableStock: b.stock,
            daysToExpiry: b.expiry
        })),
        target: {
            mode: document.getElementById("opt-mode").value,
            targetPrice: parseFloat(document.getElementById("target-price").value),

            // Checkboxes logic: if unchecked send -1
            targetAcid: document.getElementById("chk-acid").checked ? parseFloat(document.getElementById("target-acid").value) : -1,
            targetBitter: document.getElementById("chk-bitter").checked ? parseFloat(document.getElementById("target-bitter").value) : -1,
            targetSweet: document.getElementById("chk-sweet").checked ? parseFloat(document.getElementById("target-sweet").value) : -1,
            targetCaffeine: document.getElementById("chk-caf").checked ? parseFloat(document.getElementById("target-caf").value) : -1,

            totalOutputKg: parseFloat(document.getElementById("total-output").value),
            maxBatchTypes: parseInt(document.getElementById("max-types").value),
            minRatio: 0.05
        },
        algorithm: document.getElementById("algorithm-select").value,
        params: {
            priceTolerance: parseFloat(document.getElementById("tol-price").value),
            flavorTolerance: parseFloat(document.getElementById("tol-flavor").value),
            flavorPenaltyPerUnit: parseFloat(document.getElementById("pen-flavor").value),
            expiryPenaltyPerDay: parseFloat(document.getElementById("pen-expiry").value),

            weightAcid: parseFloat(document.getElementById("w-acid").value),
            weightBitter: parseFloat(document.getElementById("w-bitter").value),
            weightSweet: parseFloat(document.getElementById("w-sweet").value),
            weightCaffeine: parseFloat(document.getElementById("w-caf").value),

            solverTimeoutSec: parseFloat(document.getElementById("solver-timeout").value)
        }
    };

    try {
        const response = await fetch("/api/v1/optimize", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(requestPayload)
        });

        const result = await response.json();
        renderResults(result, requestPayload.target);

    } catch (e) {
        alert("Có lỗi xảy ra: " + e.message);
    } finally {
        btn.textContent = "🚀 Chạy Tối Ưu Hóa";
        btn.disabled = false;
    }
}

function renderResults(result, target) {
    document.getElementById("empty-state").classList.add("hidden");
    document.getElementById("results-content").classList.remove("hidden");
    document.getElementById("result-status").textContent = result.feasible ? "Thành công" : "Không tìm thấy";
    document.getElementById("result-status").style.background = result.feasible ? "var(--accent-green)" : "var(--accent-red)";

    if (!result.feasible) {
        alert("Không tìm thấy phương án phối trộn thỏa mãn các ràng buộc!");
        return;
    }

    // Retry Alert
    const retryAlert = document.getElementById("retry-alert");
    if (result.retryCount > 0) {
        retryAlert.classList.remove("hidden");
        document.getElementById("retry-trace").textContent = result.relaxationTrace;
    } else {
        retryAlert.classList.add("hidden");
    }

    // Metric Cards
    document.getElementById("res-price").textContent = parseInt(result.predictedPrice).toLocaleString() + " đ";
    const diff = result.predictedPrice - target.targetPrice;
    const diffEl = document.getElementById("res-price-diff");
    diffEl.textContent = (diff > 0 ? "+" : "") + parseInt(diff).toLocaleString() + " vs Mục tiêu";
    diffEl.className = "sub " + (diff <= 0 ? "good" : "bad");

    document.getElementById("res-score").textContent = result.objectiveValue.toFixed(2);
    document.getElementById("res-time").textContent = result.computationTimeMs + " ms";

    // Table
    const tbody = document.getElementById("recipe-body");
    tbody.innerHTML = "";
    Object.keys(result.composition).forEach(id => {
        const pct = result.composition[id];
        const weight = result.weightDistribution[id];
        const batch = state.batches.find(b => b.id === id);

        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${batch ? batch.name : id}</td>
            <td>${(pct * 100).toFixed(1)}%</td>
            <td>${weight.toFixed(1)} kg</td>
            <td>${parseInt(weight * batch.price).toLocaleString()} đ</td>
        `;
        tbody.appendChild(row);
    });

    // Charts
    renderCompositionChart(result);
    renderRadarChart(result, target);
}

function renderCompositionChart(result) {
    const ctx = document.getElementById("compositionChart").getContext("2d");
    if (charts.pie) charts.pie.destroy();

    const labels = Object.keys(result.composition).map(id => {
        const b = state.batches.find(x => x.id === id);
        return b ? b.name : id;
    });
    const data = Object.values(result.composition).map(v => v * 100);

    charts.pie = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: data,
                backgroundColor: ['#E67E22', '#D35400', '#F39C12', '#F1C40F', '#8E44AD', '#2980B9'],
                borderWidth: 0
            }]
        },
        options: {
            responsive: true,
            plugins: {
                legend: { position: 'right', labels: { color: '#ccc' } }
            }
        }
    });
}

function renderRadarChart(result, target) {
    const ctx = document.getElementById("radarChart").getContext("2d");
    if (charts.radar) charts.radar.destroy();

    charts.radar = new Chart(ctx, {
        type: 'radar',
        data: {
            labels: ['Chua (Acid)', 'Đắng (Bitter)', 'Ngọt (Sweet)', 'Caffeine'],
            datasets: [
                {
                    label: 'Dự kiến',
                    data: [result.predictedAcid, result.predictedBitter, result.predictedSweet, result.predictedCaffeine],
                    borderColor: '#E67E22',
                    backgroundColor: 'rgba(230, 126, 34, 0.2)'
                },
                {
                    label: 'Mục tiêu',
                    data: [target.targetAcid, target.targetBitter, target.targetSweet, target.targetCaffeine],
                    borderColor: '#2ECC71',
                    borderDash: [5, 5],
                    fill: false
                }
            ]
        },
        options: {
            responsive: true,
            scales: {
                r: {
                    angleLines: { color: '#333' },
                    grid: { color: '#333' },
                    pointLabels: { color: '#ccc' },
                    suggestedMin: 0,
                    suggestedMax: 10
                }
            },
            plugins: {
                legend: { labels: { color: '#ccc' } }
            }
        }
    });
}

// === COMPARISON LOGIC ===

async function runComparison() {
    const btn = document.getElementById("compare-btn");
    btn.textContent = "⏳ Đang chạy...";
    btn.disabled = true;

    // Base Payload (Clone from collect logic but override mode)
    const basePayload = {
        batches: state.batches.map(b => ({
            id: b.id, name: b.name, price: b.price,
            acid: b.acid, bitter: b.bitter, sweet: b.sweet, caffeine: b.caffeine,
            availableStock: b.stock, daysToExpiry: b.expiry
        })),
        target: {
            targetPrice: parseFloat(document.getElementById("target-price").value),
            targetAcid: document.getElementById("chk-acid").checked ? parseFloat(document.getElementById("target-acid").value) : -1,
            targetBitter: document.getElementById("chk-bitter").checked ? parseFloat(document.getElementById("target-bitter").value) : -1,
            targetSweet: document.getElementById("chk-sweet").checked ? parseFloat(document.getElementById("target-sweet").value) : -1,
            targetCaffeine: document.getElementById("chk-caf").checked ? parseFloat(document.getElementById("target-caf").value) : -1,
            totalOutputKg: parseFloat(document.getElementById("total-output").value),
            maxBatchTypes: parseInt(document.getElementById("max-types").value),
            minRatio: 0.05
        },
        algorithm: document.getElementById("algorithm-select").value,
        params: {
            priceTolerance: parseFloat(document.getElementById("tol-price").value),
            flavorTolerance: parseFloat(document.getElementById("tol-flavor").value),
            flavorPenaltyPerUnit: parseFloat(document.getElementById("pen-flavor").value),
            expiryPenaltyPerDay: parseFloat(document.getElementById("pen-expiry").value),
            weightAcid: parseFloat(document.getElementById("w-acid").value),
            weightBitter: parseFloat(document.getElementById("w-bitter").value),
            weightSweet: parseFloat(document.getElementById("w-sweet").value),
            weightCaffeine: parseFloat(document.getElementById("w-caf").value),
            solverTimeoutSec: parseFloat(document.getElementById("solver-timeout").value)
        }
    };

    const modes = ["PRICE_OPTIMIZED", "BALANCED", "QUALITY_OPTIMIZED"];
    const modeNames = {
        "PRICE_OPTIMIZED": "💰 Giá (Mass)",
        "BALANCED": "⚖️ Cân bằng",
        "QUALITY_OPTIMIZED": "🌟 Chất lượng (Specialty)"
    };

    try {
        const promises = modes.map(mode => {
            const payload = JSON.parse(JSON.stringify(basePayload));
            payload.target.mode = mode;
            return fetch("/api/v1/optimize", {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            }).then(r => r.json()).then(res => ({ mode: mode, result: res }));
        });

        const results = await Promise.all(promises);
        renderComparisonTable(results, modeNames);
        document.getElementById("compare-modal").classList.remove("hidden");

    } catch (e) {
        alert("Lỗi khi so sánh: " + e.message);
    } finally {
        btn.textContent = "⚖️ So sánh 3 Chế độ";
        btn.disabled = false;
    }
}

function renderComparisonTable(results, modeNames) {
    const container = document.getElementById("compare-body");

    let html = `<div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 15px;">`;

    results.forEach(item => {
        const r = item.result;
        const color = r.feasible ? "var(--accent-green)" : "var(--accent-red)";

        // Build Composition list
        let compHtml = "";
        if (r.composition) {
            Object.keys(r.composition).forEach(id => {
                const b = state.batches.find(x => x.id === id);
                compHtml += `<div>${b ? b.name : id}: <b>${(r.composition[id] * 100).toFixed(0)}%</b></div>`;
            });
        }

        html += `
        <div class="metric-card" style="text-align: left; border: 1px solid var(--border);">
            <h3 style="color: var(--primary); border-bottom: 1px solid var(--border); padding-bottom: 5px;">${modeNames[item.mode]}</h3>
            <div style="margin: 10px 0;">
                <span class="badge" style="background: ${color}">${r.feasible ? "Thành công" : "Thất bại"}</span>
                ${r.retryCount > 0 ? `<span class="badge" style="background: #E67E22; font-size: 0.7em;">Retry: ${r.retryCount}</span>` : ""}
            </div>
            <p>Giá: <b style="font-size: 1.2em">${parseInt(r.predictedPrice).toLocaleString()}</b></p>
            <p style="font-size: 0.9em; color: #aaa;">Score: ${r.objectiveValue.toFixed(2)}</p>
            <hr style="border: 0; border-top: 1px dashed var(--border); margin: 10px 0;">
            <div style="font-size: 0.9em;">
                ${compHtml}
            </div>
            <hr style="border: 0; border-top: 1px dashed var(--border); margin: 10px 0;">
             <div style="font-size: 0.85em; color: #ccc;">
                <p>A: ${r.predictedAcid.toFixed(1)} / B: ${r.predictedBitter.toFixed(1)}</p>
                <p>S: ${r.predictedSweet.toFixed(1)} / C: ${r.predictedCaffeine.toFixed(1)}</p>
            </div>
        </div>
        `;
    });

    html += `</div>`;
    container.innerHTML = html;
}
