#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PORT="${SERVER_PORT:-8080}"
PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
APP_MAIN_CLASS="com.partygameonline.PartyGameOnlineApplication"

log() {
    printf '[run.sh] %s\n' "$*"
}

fail() {
    printf '[run.sh] ERROR: %s\n' "$*" >&2
    exit 1
}

process_command() {
    ps -p "$1" -o command= 2>/dev/null || true
}

is_this_backend() {
    local pid="$1"
    local command
    command="$(process_command "$pid")"

    [[ "$command" == *"$APP_MAIN_CLASS"* ]] ||
        [[ "$command" == *"$SCRIPT_DIR/target/classes"* ]] ||
        [[ "$command" == *"$SCRIPT_DIR"* && "$command" == *"partygameonline-server"* ]]
}

app_process_root() {
    local pid="$1"
    local parent_pid parent_command

    while :; do
        parent_pid="$(ps -p "$pid" -o ppid= 2>/dev/null | tr -d '[:space:]')"
        [[ "$parent_pid" =~ ^[0-9]+$ ]] || break
        (( parent_pid > 1 )) || break

        parent_command="$(process_command "$parent_pid")"
        if [[ "$parent_command" == *"$SCRIPT_DIR"* ]] &&
            [[ "$parent_command" == *"java"* || "$parent_command" == *"mvn"* ]]; then
            pid="$parent_pid"
        else
            break
        fi
    done

    printf '%s\n' "$pid"
}

process_tree_pids() {
    local pid="$1"
    local child

    while IFS= read -r child; do
        [[ -n "$child" ]] || continue
        process_tree_pids "$child"
    done < <(pgrep -P "$pid" 2>/dev/null || true)

    printf '%s\n' "$pid"
}

stop_process_tree() {
    local root_pid="$1"
    local -a pids=()
    local pid attempt

    while IFS= read -r pid; do
        [[ "$pid" =~ ^[0-9]+$ ]] && pids+=("$pid")
    done < <(process_tree_pids "$root_pid")

    ((${#pids[@]} > 0)) || return 0

    # TERM allows Spring Boot to perform its configured graceful shutdown.
    kill -TERM "${pids[@]}" 2>/dev/null || true
    # Resume a process paused by a debugger/terminal so it can handle TERM.
    kill -CONT "${pids[@]}" 2>/dev/null || true

    for attempt in {1..30}; do
        local any_running=false
        for pid in "${pids[@]}"; do
            if kill -0 "$pid" 2>/dev/null; then
                any_running=true
                break
            fi
        done
        [[ "$any_running" == false ]] && return 0
        sleep 0.2
    done

    log "Backend did not stop gracefully; forcing it to stop."
    kill -KILL "${pids[@]}" 2>/dev/null || true
}

stop_existing_backend() {
    command -v lsof >/dev/null 2>&1 || fail "lsof is required to inspect port $PORT."

    local -a listener_pids=()
    local -a stopped_roots=()
    local pid root_pid command stopped_root

    while IFS= read -r pid; do
        [[ "$pid" =~ ^[0-9]+$ ]] && listener_pids+=("$pid")
    done < <(lsof -nP -tiTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | sort -u || true)

    ((${#listener_pids[@]} > 0)) || {
        log "Port $PORT is free."
        return 0
    }

    for pid in "${listener_pids[@]}"; do
        command="$(process_command "$pid")"
        if ! is_this_backend "$pid"; then
            fail "Port $PORT is being used by another application (PID $pid): $command"
        fi

        root_pid="$(app_process_root "$pid")"
        for stopped_root in "${stopped_roots[@]:-}"; do
            [[ "$stopped_root" == "$root_pid" ]] && continue 2
        done

        log "Stopping old partygameonline backend (PID $root_pid) on port $PORT..."
        stop_process_tree "$root_pid"
        stopped_roots+=("$root_pid")
    done

    for _ in {1..20}; do
        if ! lsof -nP -tiTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
            log "Port $PORT is now free."
            return 0
        fi
        sleep 0.2
    done

    fail "Could not release port $PORT."
}

# Always clear an older instance before checking/building the new one.
stop_existing_backend

[[ "$(uname -s)" == "Darwin" ]] || fail "This script is intended for macOS."

if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)" ||
        fail "Java 21 was not found. Install JDK 21 and try again."
    export JAVA_HOME
fi
export PATH="$JAVA_HOME/bin:$PATH"

[[ -x "$SCRIPT_DIR/mvnw" ]] || fail "Maven Wrapper is missing or not executable: $SCRIPT_DIR/mvnw"

log "Starting Spring Boot with profile '$PROFILE' on port $PORT..."
cd "$SCRIPT_DIR"
exec ./mvnw spring-boot:run "-Dspring-boot.run.profiles=$PROFILE"
