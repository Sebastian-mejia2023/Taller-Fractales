#!/usr/bin/env bash
set -euo pipefail

RUN_ID="${RUN_ID:-exp2_$(date +%Y%m%d_%H%M%S)}"
export RUN_ID

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

THREAD_COUNTS="${THREAD_COUNTS:-1 2 4 64}"
STRATEGY_TYPE="${STRATEGY_TYPE:-1}"
STRATEGY_PARAM="${STRATEGY_PARAM:-64}"
WORKER_HOST="${WORKER_HOST:-127.0.0.1}"

ensure_dirs
build_distributions
rm -f "$RESULTS_DIR/experiment2.csv" "$RESULTS_DIR/experiment2_worker_stats.csv"
trap cleanup EXIT INT TERM

start_master
index=0
for threads in $THREAD_COUNTS; do
    index=$((index + 1))
    worker_id="Worker-T${threads}"
    worker_port="$((WORKER_BASE_PORT + index - 1))"

    start_worker "$worker_id" "$worker_port" "$threads" "$WORKER_HOST"

    for ((rep = 1; rep <= REPS; rep++)); do
        run_label="exp2_threads${threads}_r${rep}"
        image="$IMAGES_DIR/${run_label}.png"
        echo "[INFO] Experimento 2: ${threads} hilo(s), repeticion ${rep}/${REPS}"
        run_client "$STRATEGY_TYPE" "$STRATEGY_PARAM" "$image" "$run_label" \
            "$RESULTS_DIR/experiment2.csv" "$RESULTS_DIR/experiment2_worker_stats.csv"
    done

    stop_worker "${WORKER_PIDS[-1]}"
done

echo "[INFO] Resultados: $RESULTS_DIR/experiment2.csv"
echo "[INFO] Metricas por Worker: $RESULTS_DIR/experiment2_worker_stats.csv"
echo "[INFO] Logs: $LOG_DIR"
