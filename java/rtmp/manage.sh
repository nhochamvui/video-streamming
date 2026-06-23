#!/usr/bin/env bash
set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────────
IMAGE="ghcr.io/nhochamvui/video-streamming"
SERVICE_CONTAINER="rtmp-server"
WATCHTOWER_CONTAINER="watchtower"
SERVICE_PORT_HTTP="8888"
SERVICE_PORT_RTMP="1935"
# ─── /Configuration ───────────────────────────────────────────────────────────

usage() {
    cat <<EOF
Usage: $(basename "$0") <command>

Commands:
  pre-install      Install Docker and set up system dependencies
  start            Pull latest image, start service + Watchtower
  stop             Stop and remove service + Watchtower containers
  restart          Stop then start
  watch            Tail logs of the service container
  monitor          One-shot container & host resource snapshot
  monitor --live   Live-refreshing dashboard (every 2s)

Examples:
  $(basename "$0") pre-install
  $(basename "$0") start
  $(basename "$0") monitor --live
EOF
    exit 1
}

info()  { echo "[INFO]  $*"; }
warn()  { echo "[WARN]  $*" >&2; }
fatal() { echo "[FATAL] $*" >&2; exit 1; }

require_docker() {
    command -v docker &>/dev/null || fatal "Docker is not installed. Run '$0 pre-install' first."
}

pre_install() {
    if command -v docker &>/dev/null; then
        info "Docker is already installed."
        return
    fi

    info "Installing Docker via official script..."
    curl -fsSL https://get.docker.com | sh

    info "Adding current user to the docker group..."
    sudo usermod -aG docker "$USER"

    info "Enabling and starting Docker systemd service..."
    sudo systemctl enable docker
    sudo systemctl start docker

    echo ""
    info "Docker installed successfully."
    warn "Log out and back in (or run 'newgrp docker') for group changes to take effect."
}

start_service() {
    require_docker

    mkdir -p ./hls

    info "Pulling latest image: ${IMAGE}:latest"
    docker pull "${IMAGE}:latest"

    # Remove old containers if they still exist (ignore error if not found)
    docker rm -f "${SERVICE_CONTAINER}" 2>/dev/null || true
    docker rm -f "${WATCHTOWER_CONTAINER}" 2>/dev/null || true

    info "Starting service container: ${SERVICE_CONTAINER}"
    docker run -d \
        --name "${SERVICE_CONTAINER}" \
        --restart unless-stopped \
        -p "${SERVICE_PORT_RTMP}:${SERVICE_PORT_RTMP}" \
        -p "${SERVICE_PORT_HTTP}:${SERVICE_PORT_HTTP}" \
        -v "$(pwd)/hls:/app/hls" \
        "${IMAGE}:latest"

    info "Starting Watchtower container: ${WATCHTOWER_CONTAINER}"
    docker run -d \
        --name "${WATCHTOWER_CONTAINER}" \
        --restart unless-stopped \
        -v /var/run/docker.sock:/var/run/docker.sock \
        containrrr/watchtower \
        --interval 60 \
        --cleanup

    info "Service started. Use '$0 watch' to follow logs."
}

stop_service() {
    require_docker

    info "Stopping and removing service container..."
    docker stop "${SERVICE_CONTAINER}" 2>/dev/null || true
    docker rm   "${SERVICE_CONTAINER}" 2>/dev/null || true

    info "Stopping and removing Watchtower container..."
    docker stop "${WATCHTOWER_CONTAINER}" 2>/dev/null || true
    docker rm   "${WATCHTOWER_CONTAINER}" 2>/dev/null || true

    info "All containers stopped and removed."
}

restart_service() {
    stop_service
    start_service
}

watch_logs() {
    require_docker
    docker logs -f "${SERVICE_CONTAINER}" 2>/dev/null || {
        warn "Container '${SERVICE_CONTAINER}' is not running. Showing last available logs:"
        docker logs "${SERVICE_CONTAINER}" 2>/dev/null || fatal "Container '${SERVICE_CONTAINER}' does not exist. Start it first with '$0 start'."
    }
}

monitor() {
    require_docker

    if [[ "${1:-}" == "--live" ]]; then
        if command -v watch &>/dev/null; then
            exec watch -n 2 "$0" monitor
        else
            while true; do
                clear
                "$0" monitor
                sleep 2
            done
        fi
        return
    fi

    local hw=58

    echo "┌──────────────────────────────────────────────────────────────┐"

    if docker inspect "${SERVICE_CONTAINER}" &>/dev/null 2>&1; then
        local stats cpu mem net blk status started
        stats=$(docker stats --no-stream --format $'{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}' "${SERVICE_CONTAINER}" 2>/dev/null)
        IFS=$'\t' read -r cpu mem net blk <<< "$stats" 2>/dev/null || { cpu="N/A"; mem="N/A"; net="N/A"; blk="N/A"; }

        status=$(docker inspect --format '{{.State.Status}}' "${SERVICE_CONTAINER}" 2>/dev/null)
        started=$(docker inspect --format '{{.State.StartedAt}}' "${SERVICE_CONTAINER}" 2>/dev/null | cut -d'.' -f1 | tr 'T' ' ')

        local img
        img=$(docker inspect --format '{{.Config.Image}}' "${SERVICE_CONTAINER}" 2>/dev/null || echo "${IMAGE}:latest")
        printf "│  %-${hw}s│\n" "Container: ${SERVICE_CONTAINER}  (${img})"
        printf "│  %-${hw}s│\n" "CPU: ${cpu}   MEM: ${mem}"
        printf "│  %-${hw}s│\n" "NET: ${net}   BLK: ${blk}"
        printf "│  %-${hw}s│\n" "Status: ${status}   Since: ${started}"

        local code rtime
        code=$(curl -o /dev/null -s -w "%{http_code}" --connect-timeout 3 http://localhost:8888 2>/dev/null || echo "000")
        rtime=$(curl -o /dev/null -s -w "%{time_total}" --connect-timeout 3 http://localhost:8888 2>/dev/null || echo "0")
        if [[ "$code" != "000" ]]; then
            printf "│  %-${hw}s│\n" "Health: localhost:8888 → ${code} (${rtime}s)"
        else
            printf "│  %-${hw}s│\n" "Health: localhost:8888 → unreachable"
        fi
    else
        printf "│  %-${hw}s│\n" "Container: ${SERVICE_CONTAINER}"
        printf "│  %-${hw}s│\n" "(not running)"
    fi

    echo "├──────────────────────────────┬───────────────────────────────┤"

    local disk_info ram_info uptime_str load
    disk_info=$(df -h / | awk 'NR==2{printf "%s/%s (%s)", $3, $2, $5}')
    ram_info=$(free -h | awk 'NR==2{printf "%s/%s", $3, $2}')
    uptime_str=$(uptime -p | sed 's/^up //')
    load=$(uptime | awk -F'load average:' '{print $2}' | xargs)

    printf "│  %-28s│  %-29s│\n" "Disk: ${disk_info}" "RAM: ${ram_info}"
    printf "│  %-28s│  %-29s│\n" "Up: ${uptime_str}" "Load: ${load}"

    echo "└──────────────────────────────┴───────────────────────────────┘"
}

# ─── Dispatch ─────────────────────────────────────────────────────────────────
case "${1:-}" in
    pre-install) pre_install ;;
    start)       start_service ;;
    stop)        stop_service ;;
    restart)     restart_service ;;
    watch)       watch_logs ;;
    monitor)     monitor "${2:-}" ;;
    *)           usage ;;
esac
