#!/usr/bin/env bash
set -euo pipefail

RUN_ID="${RUN_ID:-exp3_$(date +%Y%m%d_%H%M%S)}"
export RUN_ID

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

P_VALUES="${P_VALUES:-1 2 4}"
THREADS="${THREADS:-1}"
STRATEGY_TYPE="${STRATEGY_TYPE:-1}"
STRATEGY_PARAM="${STRATEGY_PARAM:-64}"
WORKER_HOST="${WORKER_HOST:-127.0.0.1}"

ensure_dirs
build_distributions
rm -f "$RESULTS_DIR/experiment3.csv" "$RESULTS_DIR/experiment3_worker_stats.csv"
trap cleanup EXIT INT TERM

start_master
worker_index=0
for p in $P_VALUES; do
    for ((i = 1; i <= p; i++)); do
        worker_index=$((worker_index + 1))
        worker_id="Worker-P${p}-${i}"
        worker_port="$((WORKER_BASE_PORT + worker_index - 1))"
        start_worker "$worker_id" "$worker_port" "$THREADS" "$WORKER_HOST"
    done

    for ((rep = 1; rep <= REPS; rep++)); do
        run_label="exp3_p${p}_r${rep}"
        image="$IMAGES_DIR/${run_label}.png"
        echo "[INFO] Experimento 3: ${p} worker(s), ${THREADS} hilo(s) por worker, repeticion ${rep}/${REPS}"
        run_client "$STRATEGY_TYPE" "$STRATEGY_PARAM" "$image" "$run_label" \
            "$RESULTS_DIR/experiment3.csv" "$RESULTS_DIR/experiment3_worker_stats.csv"
    done

    stop_all_workers
done

echo "[INFO] Resultados: $RESULTS_DIR/experiment3.csv"
echo "[INFO] Metricas por Worker: $RESULTS_DIR/experiment3_worker_stats.csv"
echo "[INFO] Logs: $LOG_DIR"
