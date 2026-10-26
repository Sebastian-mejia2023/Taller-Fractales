#!/usr/bin/env bash
set -euo pipefail

RUN_ID="${RUN_ID:-exp1_$(date +%Y%m%d_%H%M%S)}"
export RUN_ID

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

WORKERS="${WORKERS:-2}"
THREADS="${THREADS:-2}"
WORKER_HOST="${WORKER_HOST:-127.0.0.1}"

ensure_dirs
build_distributions
rm -f "$RESULTS_DIR/experiment1.csv" "$RESULTS_DIR/experiment1_worker_stats.csv"
trap cleanup EXIT INT TERM

start_master
for ((i = 1; i <= WORKERS; i++)); do
    start_worker "Worker-$i" "$((WORKER_BASE_PORT + i - 1))" "$THREADS" "$WORKER_HOST"
done

echo "[INFO] Ejecutando Experimento 1: ${WIDTH}x${HEIGHT}, ${MAXITER} iteraciones, preset ${PRESET}, ${REPS} repeticion(es)"
run_experiment1_runner

echo "[INFO] Resultados: $RESULTS_DIR/experiment1.csv"
echo "[INFO] Metricas por Worker: $RESULTS_DIR/experiment1_worker_stats.csv"
echo "[INFO] Logs: $LOG_DIR"
