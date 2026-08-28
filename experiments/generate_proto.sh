#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p experiments/generated
python -m grpc_tools.protoc -Icontracts --python_out=experiments/generated \
  --grpc_python_out=experiments/generated contracts/stock_reservation.proto
