#!/usr/bin/env bash
set -u

docs_dir="${1:-aiops-docs}"
upload_api="${2:-http://localhost:9999/api/upload}"

echo "Uploading documents from ${docs_dir}..."

if [ ! -d "${docs_dir}" ]; then
  echo "Directory does not exist: ${docs_dir}"
  exit 1
fi

count=0
success=0
failed=0

for file in "${docs_dir}"/*.md; do
  if [ ! -f "${file}" ]; then
    continue
  fi

  count=$((count + 1))
  filename="$(basename "${file}")"
  echo "  [${count}] Uploading: ${filename}"

  body_file="$(mktemp)"
  http_code="$(curl -s -o "${body_file}" -w "%{http_code}" -X POST "${upload_api}" \
    -F "file=@${file}" \
    -H "Accept: application/json")"

  if [ "${http_code}" = "200" ]; then
    echo "      OK: ${filename}"
    success=$((success + 1))
  else
    echo "      FAILED: ${filename} (HTTP ${http_code})"
    head -n 3 "${body_file}"
    failed=$((failed + 1))
  fi

  rm -f "${body_file}"
  sleep 1
done

echo ""
echo "Upload summary:"
echo "   Total: ${count}"
echo "   Success: ${success}"
if [ "${failed}" -gt 0 ]; then
  echo "   Failed: ${failed}"
  exit 1
fi
