#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RUN_ID="${RUN_ID:-$(date +%Y%m%d_%H%M%S)}"
RESULTS_DIR="${RESULTS_DIR:-$ROOT_DIR/results}"
LOG_DIR="${LOG_DIR:-$RESULTS_DIR/logs}"
CACHE_DIR="$RESULTS_DIR/cache"
IMAGES_DIR="$RESULTS_DIR/images"
GRAPH_DIR="$RESULTS_DIR/graphs"

MASTER_PORT="${MASTER_PORT:-10000}"
WORKER_BASE_PORT="${WORKER_BASE_PORT:-10001}"
WIDTH="${WIDTH:-3840}"
HEIGHT="${HEIGHT:-2160}"
MAXITER="${MAXITER:-5000}"
PRESET="${PRESET:-seahorse}"
REPS="${REPS:-1}"

SERVER_CP="$ROOT_DIR/server/build/install/server/lib/*"
WORKER_CP="$ROOT_DIR/worker/build/install/worker/lib/*"
CLIENT_CP="$ROOT_DIR/client/build/install/client/lib/*"
MONOLITH_CP="$ROOT_DIR/monolith/build/install/monolith/lib/*"

MASTER_PID=""
declare -a WORKER_PIDS=()

ensure_dirs() {
    mkdir -p "$RESULTS_DIR" "$LOG_DIR" "$CACHE_DIR" "$IMAGES_DIR" "$GRAPH_DIR"
}

build_distributions() {
    if [[ -z "${ICE_HOME:-}" && -x "$HOME/.local/opt/zeroc-ice/usr/bin/slice2java" ]]; then
        export ICE_HOME="$HOME/.local/opt/zeroc-ice/usr"
    fi
    if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
        "$ROOT_DIR/gradlew" -p "$ROOT_DIR" \
            :server:installDist :worker:installDist :client:installDist :monolith:installDist \
            --console=plain
    fi
}

wait_for_log() {
    local file="$1"
    local pattern="$2"
    local attempts="${3:-100}"
    local i
    for ((i = 0; i < attempts; i++)); do
        if [[ -f "$file" ]] && grep -Fq -- "$pattern" "$file"; then
            return 0
        fi
        sleep 0.2
    done
    return 1
}

start_master() {
    local log_file="$LOG_DIR/master_${RUN_ID}.log"
    java -cp "$SERVER_CP" co.icesi.fractal.master.MasterMain "$MASTER_PORT" >"$log_file" 2>&1 &
    MASTER_PID=$!

    if ! wait_for_log "$log_file" "NODO MAESTRO (MASTER) INICIADO" 100; then
        echo "[ERROR] El Maestro no inicio. Revisar $log_file" >&2
        exit 1
    fi
    echo "[INFO] Maestro iniciado en puerto $MASTER_PORT (PID $MASTER_PID)"
}

start_worker() {
    local worker_id="$1"
    local worker_port="$2"
    local threads="$3"
    local host="${4:-127.0.0.1}"
    local log_file="$LOG_DIR/worker_${worker_id}_${RUN_ID}.log"

    java -cp "$WORKER_CP" co.icesi.fractal.worker.WorkerMain \
        "$worker_port" "$worker_id" localhost "$MASTER_PORT" "$threads" "$host" \
        >"$log_file" 2>&1 &
    local pid=$!
    WORKER_PIDS+=("$pid")

    if ! wait_for_log "$LOG_DIR/master_${RUN_ID}.log" "[+] Worker registrado: $worker_id" 100; then
        echo "[ERROR] El Worker $worker_id no se registro. Revisar $log_file" >&2
        exit 1
    fi
    echo "[INFO] Worker $worker_id registrado en puerto $worker_port con $threads hilo(s) (PID $pid)"
}

stop_worker() {
    local pid="$1"
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
    sleep 0.2
}

stop_all_workers() {
    local pid
    for pid in "${WORKER_PIDS[@]:-}"; do
        if [[ -n "$pid" ]]; then
            stop_worker "$pid"
        fi
    done
    WORKER_PIDS=()
}

stop_master() {
    if [[ -n "$MASTER_PID" ]]; then
        kill "$MASTER_PID" 2>/dev/null || true
        wait "$MASTER_PID" 2>/dev/null || true
        MASTER_PID=""
    fi
}

cleanup() {
    stop_all_workers
    stop_master
}

baseline_cache_path() {
    echo "$CACHE_DIR/baseline_${PRESET}_${WIDTH}x${HEIGHT}_${MAXITER}.bin"
}

run_client() {
    local strategy_type="$1"
    local strategy_param="$2"
    local output_image="$3"
    local run_label="$4"
    local csv_path="$5"
    local worker_csv_path="$6"

    FRACTAL_BASELINE_CACHE="$(baseline_cache_path)" \
    FRACTAL_RESULTS_CSV="$csv_path" \
    FRACTAL_WORKER_CSV="$worker_csv_path" \
    FRACTAL_RUN_LABEL="$run_label" \
    java -cp "$CLIENT_CP" co.icesi.fractal.client.ClientMain \
        localhost "$MASTER_PORT" "$WIDTH" "$HEIGHT" "$MAXITER" \
        "$strategy_type" "$strategy_param" "$PRESET" "$output_image"
}

run_experiment1_runner() {
    local csv_path="$RESULTS_DIR/experiment1.csv"
    local worker_csv_path="$RESULTS_DIR/experiment1_worker_stats.csv"

    FRACTAL_BASELINE_CACHE="$(baseline_cache_path)" \
    java -cp "$CLIENT_CP" co.icesi.fractal.client.ExperimentRunner \
        localhost "$MASTER_PORT" "$WIDTH" "$HEIGHT" "$MAXITER" "$PRESET" \
        "$REPS" "$csv_path" "$worker_csv_path"
}
