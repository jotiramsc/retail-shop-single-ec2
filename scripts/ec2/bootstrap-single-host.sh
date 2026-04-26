#!/bin/sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
  echo "Run this script as root on the EC2 instance."
  exit 1
fi

ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
DATA_ROOT="${DATA_ROOT:-/mnt/retail-data}"
DOCKER_NETWORK="${DOCKER_NETWORK:-retail-shop-net}"
DEFAULT_USER="${SUDO_USER:-ubuntu}"

apt-get update
apt-get install -y ca-certificates curl gnupg lsb-release

install -m 0755 -d /etc/apt/keyrings
if [ ! -f /etc/apt/keyrings/docker.asc ]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
fi

ARCH="$(dpkg --print-architecture)"
CODENAME="$(. /etc/os-release && echo "$VERSION_CODENAME")"
echo "deb [arch=$ARCH signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $CODENAME stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable --now docker
usermod -aG docker "$DEFAULT_USER" || true

mkdir -p "$DATA_ROOT/postgres" "$DATA_ROOT/backups" "$DATA_ROOT/app"
chown -R 999:999 "$DATA_ROOT/postgres"

docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK"

echo "Bootstrap complete."
echo "If an empty EBS volume is attached, run:"
echo "  sudo EBS_DEVICE=/dev/nvme1n1 $ROOT_DIR/scripts/ec2/mount-ebs-volume.sh"
