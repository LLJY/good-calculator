#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
WASM_ASSET_DIR="$ROOT_DIR/app/src/main/assets/wasm"
JNILIBS_DIR="$ROOT_DIR/app/src/main/jniLibs"
LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/calbot-cursed-build.XXXXXX")"
START_TIME="$(date +%s)"
KEEP_LOGS=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
RESET='\033[0m'

declare -a BUILT_WASM=()
declare -a SKIPPED_WASM=()
declare -a FAILED_WASM=()
declare -a BUILT_NATIVE=()
declare -a SKIPPED_NATIVE=()
declare -a WARNINGS=()
declare -a PIDS=()
declare -A PID_TO_NAME=()
declare -A PID_TO_LOG=()

cleanup() {
    if [ "$KEEP_LOGS" -eq 0 ]; then
        rm -rf "$LOG_DIR"
    fi
}

on_error() {
    local exit_code=$?
    local line_no="$1"
    local command_text="$2"

    KEEP_LOGS=1

    printf "%b\n" "${RED}${BOLD}Build failed${RESET} at line ${line_no}: ${command_text}"
    printf "%b\n" "${YELLOW}Logs are in:${RESET} $LOG_DIR"
    exit "$exit_code"
}

trap 'on_error "$LINENO" "$BASH_COMMAND"' ERR
trap cleanup EXIT

say() {
    printf "%b\n" "$1"
}

step() {
    say "${BLUE}${BOLD}==>${RESET} $1"
}

good() {
    say "${GREEN}OK${RESET} $1"
}

warn() {
    WARNINGS+=("$1")
    say "${YELLOW}WARN${RESET} $1"
}

human_size() {
    local file_path="$1"
    local bytes

    if [ ! -f "$file_path" ]; then
        printf "missing"
        return 0
    fi

    bytes="$(wc -c < "$file_path")"
    if command -v numfmt >/dev/null 2>&1; then
        printf "%s (%s bytes)" "$(numfmt --to=iec-i --suffix=B "$bytes")" "$bytes"
    else
        printf "%s bytes" "$bytes"
    fi
}

require_tool() {
    local tool_name="$1"
    if command -v "$tool_name" >/dev/null 2>&1; then
        good "Found tool: $tool_name"
    else
        warn "Missing tool: $tool_name"
    fi
}

have_tool() {
    command -v "$1" >/dev/null 2>&1
}

banner() {
    cat <<'EOF'
===============================================================
 CalBot Cursed Build Orchestrator
===============================================================
 This unholy script attempts to build:
   - add.wasm   from Go via TinyGo
   - sub.wasm   from C via clang
   - mul.wasm   from Zig via Zig
   - div.wasm   from Rust via cargo
   - ledger.wasm from COBOL via GnuCOBOL's C detour
   - libcalbot_zig.so for Android ABIs
   - a debug APK that should have been a normal calculator
===============================================================
EOF
}

start_wasm_build() {
    local module_name="$1"
    local module_dir="$2"
    local required_tool="$3"
    local log_file="$LOG_DIR/${module_name}.log"

    if [ -n "$required_tool" ] && ! have_tool "$required_tool"; then
        warn "Skipping ${module_name}: missing ${required_tool}"
        SKIPPED_WASM+=("${module_name} (${required_tool} missing)")
        return 0
    fi

    (
        cd "$ROOT_DIR/$module_dir"
        make build
    ) >"$log_file" 2>&1 &

    PIDS+=("$!")
    PID_TO_NAME["$!"]="$module_name"
    PID_TO_LOG["$!"]="$log_file"
}

wait_for_wasm_builds() {
    local pid
    local module_name
    local log_file

    for pid in "${PIDS[@]}"; do
        module_name="${PID_TO_NAME[$pid]}"
        log_file="${PID_TO_LOG[$pid]}"
        if wait "$pid"; then
            good "Built WASM module: ${module_name}"
            BUILT_WASM+=("$module_name")
        else
            FAILED_WASM+=("$module_name")
            warn "WASM build failed for ${module_name}. See ${log_file}"
        fi
    done

    if [ "${#FAILED_WASM[@]}" -gt 0 ]; then
        say "${RED}${BOLD}One or more WASM builds failed.${RESET}"
        exit 1
    fi
}

locate_zig_output() {
    local candidates=(
        "$ROOT_DIR/zig/zig-out/lib/libcalbot_zig.so"
        "$ROOT_DIR/zig/zig-out/libcalbot_zig.so"
        "$ROOT_DIR/zig/out/lib/libcalbot_zig.so"
        "$ROOT_DIR/zig/out/libcalbot_zig.so"
    )
    local candidate

    for candidate in "${candidates[@]}"; do
        if [ -f "$candidate" ]; then
            printf "%s\n" "$candidate"
            return 0
        fi
    done

    return 1
}

build_native_variant() {
    local abi="$1"
    local target="$2"
    local log_file="$LOG_DIR/zig-${abi}.log"
    local output_path

    if [ ! -f "$ROOT_DIR/zig/build.zig" ]; then
        warn "Skipping Zig native build for ${abi}: zig/build.zig is not present yet"
        SKIPPED_NATIVE+=("${abi} (missing zig/build.zig)")
        return 0
    fi

    if ! have_tool zig; then
        warn "Skipping Zig native build for ${abi}: missing zig"
        SKIPPED_NATIVE+=("${abi} (zig missing)")
        return 0
    fi

    (
        cd "$ROOT_DIR/zig"
        zig build -Dtarget="$target" -Doptimize=ReleaseFast
    ) >"$log_file" 2>&1

    output_path="$(locate_zig_output)"
    mkdir -p "$JNILIBS_DIR/$abi"
    cp "$output_path" "$JNILIBS_DIR/$abi/libcalbot_zig.so"
    BUILT_NATIVE+=("${abi}")
    good "Built native Zig library for ${abi}"
}

report_artifact() {
    local label="$1"
    local file_path="$2"
    say " - ${label}: $(human_size "$file_path")"
}

banner

step "Checking toolchain"
require_tool make
require_tool zig
require_tool tinygo
require_tool rustc
require_tool cargo
require_tool clang
require_tool cobc

if [ ! -x "$ROOT_DIR/gradlew" ]; then
    say "${RED}${BOLD}Missing executable Gradle wrapper:${RESET} $ROOT_DIR/gradlew"
    exit 1
fi

mkdir -p "$WASM_ASSET_DIR" "$JNILIBS_DIR/arm64-v8a" "$JNILIBS_DIR/x86_64"

if ! have_tool make; then
    say "${RED}${BOLD}Cannot continue without make.${RESET}"
    exit 1
fi

step "Building WASM modules in parallel"
start_wasm_build "add-go" "wasm/add-go" "tinygo"
start_wasm_build "sub-c" "wasm/sub-c" "clang"
start_wasm_build "mul-zig" "wasm/mul-zig" "zig"
start_wasm_build "div-rust" "wasm/div-rust" "cargo"
start_wasm_build "ledger-cobol" "wasm/ledger-cobol" ""
wait_for_wasm_builds

step "Building Zig native shared libraries"
build_native_variant "arm64-v8a" "aarch64-linux-android"
build_native_variant "x86_64" "x86_64-linux-android"

step "Verifying artifacts"
report_artifact "add.wasm" "$WASM_ASSET_DIR/add.wasm"
report_artifact "sub.wasm" "$WASM_ASSET_DIR/sub.wasm"
report_artifact "mul.wasm" "$WASM_ASSET_DIR/mul.wasm"
report_artifact "div.wasm" "$WASM_ASSET_DIR/div.wasm"
report_artifact "ledger.wasm" "$WASM_ASSET_DIR/ledger.wasm"
report_artifact "arm64-v8a/libcalbot_zig.so" "$JNILIBS_DIR/arm64-v8a/libcalbot_zig.so"
report_artifact "x86_64/libcalbot_zig.so" "$JNILIBS_DIR/x86_64/libcalbot_zig.so"

step "Running Gradle assembleDebug"
(
    cd "$ROOT_DIR"
    ./gradlew assembleDebug
)

END_TIME="$(date +%s)"
DURATION="$((END_TIME - START_TIME))"

step "Build summary"
say " - Language count: 11"
say " - WASM modules built: ${#BUILT_WASM[@]}"
say " - Native ABIs built: ${#BUILT_NATIVE[@]}"
say " - Total time: ${DURATION}s"

if [ "${#SKIPPED_WASM[@]}" -gt 0 ]; then
    say " - Skipped WASM: ${SKIPPED_WASM[*]}"
fi

if [ "${#SKIPPED_NATIVE[@]}" -gt 0 ]; then
    say " - Skipped native: ${SKIPPED_NATIVE[*]}"
fi

if [ "${#WARNINGS[@]}" -gt 0 ]; then
    say " - Warnings: ${#WARNINGS[@]}"
fi

say ""
say "Artifact sizes"
report_artifact "add.wasm" "$WASM_ASSET_DIR/add.wasm"
report_artifact "sub.wasm" "$WASM_ASSET_DIR/sub.wasm"
report_artifact "mul.wasm" "$WASM_ASSET_DIR/mul.wasm"
report_artifact "div.wasm" "$WASM_ASSET_DIR/div.wasm"
report_artifact "ledger.wasm" "$WASM_ASSET_DIR/ledger.wasm"
report_artifact "arm64-v8a/libcalbot_zig.so" "$JNILIBS_DIR/arm64-v8a/libcalbot_zig.so"
report_artifact "x86_64/libcalbot_zig.so" "$JNILIBS_DIR/x86_64/libcalbot_zig.so"

good "Cursed build completed"
