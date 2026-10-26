#!/usr/bin/env python3
import csv
import os
import re
import statistics
import sys
from collections import defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULTS_DIR = os.environ.get("RESULTS_DIR", os.path.join(ROOT, "results"))
GRAPH_DIR = os.path.join(RESULTS_DIR, "graphs")


def read_rows(name):
    path = os.path.join(RESULTS_DIR, name)
    if not os.path.isfile(path):
        return []
    with open(path, newline="", encoding="utf-8") as handle:
        return [row for row in csv.DictReader(handle) if row]


def number(value, default=0.0):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def grouped_medians(rows, key_fn):
    groups = defaultdict(list)
    for row in rows:
        groups[key_fn(row)].append(row)
    result = []
    for key, items in groups.items():
        t_sec = statistics.median(number(r["t_sec_ms"]) for r in items)
        t_dist = statistics.median(number(r["t_dist_ms"]) for r in items)
        result.append({
            "key": key,
            "t_sec": t_sec,
            "t_dist": t_dist,
            "speedup": t_sec / max(1.0, t_dist),
            "efficiency": (t_sec / max(1.0, t_dist)) / max(1, len({r["workers"] for r in items})),
            "workers": items[0]["workers"],
        })
    return sorted(result, key=lambda item: item["key"])


def plot_series(series, xlabel, title, filename):
    if not series:
        print(f"[WARN] Sin datos para {filename}")
        return

    xs = [item["key"] for item in series]
    ys = [item["speedup"] for item in series]
    labels = [str(x) for x in xs]

    plt.figure(figsize=(7.5, 5.0))
    plt.plot(range(len(xs)), ys, marker="o", linewidth=2, color="#1f77b4")
    plt.axhline(1.0, color="#d62728", linestyle="--", linewidth=1, label="S = 1")
    plt.xticks(range(len(xs)), labels)
    plt.xlabel(xlabel)
    plt.ylabel("Speedup (S = T_sec / T_dist)")
    plt.title(title)
    plt.grid(True, alpha=0.3)
    plt.legend()
    for index, value in enumerate(ys):
        plt.annotate(f"{value:.2f}x", (index, value), textcoords="offset points",
                     xytext=(0, 8), ha="center", fontsize=8)
    plt.tight_layout()
    output = os.path.join(GRAPH_DIR, filename)
    plt.savefig(output, dpi=150)
    plt.close()
    print(f"[OK] {output}")


def main():
    os.makedirs(GRAPH_DIR, exist_ok=True)

    exp1 = read_rows("experiment1.csv")
    fixed = [r for r in exp1 if r.get("strategy_type") == "0"]
    tiles = [r for r in exp1 if r.get("strategy_type") == "1"]

    fixed_series = grouped_medians(fixed, lambda r: int(number(r["strategy_param"])))
    tile_series = grouped_medians(tiles, lambda r: int(number(r["strategy_param"])))

    plot_series(tile_series, "Tamaño de baldosa (px)",
                "Speedup vs. tamaño de baldosa (Exp. 1)", "speedup_vs_tile.png")
    plot_series(fixed_series, "Número de franjas fijas (N)",
                "Speedup vs. franjas fijas (Exp. 1)", "speedup_vs_fixed_count.png")

    exp2 = read_rows("experiment2.csv")
    def threads_key(row):
        match = re.search(r"threads(\d+)", row.get("run_label", ""))
        return int(match.group(1)) if match else 0
    threads_series = grouped_medians(exp2, threads_key)
    plot_series(threads_series, "Hilos de cómputo por Worker",
                "Speedup vs. hilos del ThreadPool (Exp. 2)", "speedup_vs_threads.png")

    exp3 = read_rows("experiment3.csv")
    def workers_key(row):
        match = re.search(r"_p(\d+)_", row.get("run_label", ""))
        return int(match.group(1)) if match else 0
    workers_series = grouped_medians(exp3, workers_key)
    plot_series(workers_series, "Número de Workers (p)",
                "Speedup vs. número de Workers (Exp. 3)", "speedup_vs_workers.png")

    if not exp1 and not exp2 and not exp3:
        print("[WARN] No hay CSV de experimentos en", RESULTS_DIR)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
