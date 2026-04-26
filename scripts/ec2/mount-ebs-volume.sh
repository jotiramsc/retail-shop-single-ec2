#!/bin/sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
  echo "Run this script as root so it can format and mount the EBS volume."
  exit 1
fi

: "${EBS_DEVICE:?Set EBS_DEVICE to the block device path, for example /dev/nvme1n1}"

MOUNT_POINT="${MOUNT_POINT:-/mnt/retail-data}"
FILESYSTEM="${FILESYSTEM:-ext4}"

mkdir -p "$MOUNT_POINT"

if ! blkid "$EBS_DEVICE" >/dev/null 2>&1; then
  mkfs -t "$FILESYSTEM" "$EBS_DEVICE"
fi

UUID="$(blkid -s UUID -o value "$EBS_DEVICE")"
if ! grep -q "$UUID" /etc/fstab; then
  echo "UUID=$UUID $MOUNT_POINT $FILESYSTEM defaults,nofail 0 2" >> /etc/fstab
fi

mountpoint -q "$MOUNT_POINT" || mount "$MOUNT_POINT"

mkdir -p "$MOUNT_POINT/postgres" "$MOUNT_POINT/backups" "$MOUNT_POINT/app"
chown -R 999:999 "$MOUNT_POINT/postgres"

echo "Mounted $EBS_DEVICE at $MOUNT_POINT"
