#!/usr/bin/env python3
"""Bounded local load test for the real API -> presigned S3 -> complete path.

The script creates synthetic, valid PNGs only. It refuses production-looking URLs,
caps count/concurrency/runtime/data volume, and writes a JSON report containing the
exact test identities/projects so cleanup can be narrowly targeted.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import struct
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib
from collections import Counter
from pathlib import Path
from typing import Any


MAX_COUNT = 1000
MAX_CONCURRENCY = 24
MAX_RUNTIME_SECONDS = 1800
MAX_TOTAL_BYTES = 512 * 1024 * 1024


class HttpFailure(RuntimeError):
    def __init__(self, status: int, body: dict[str, Any], headers: Any):
        super().__init__(body.get("message") or f"HTTP {status}")
        self.status = status
        self.code = body.get("code")
        self.headers = headers


def png_chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def synthetic_png(index: int, multipart: bool) -> bytes:
    width, height = (2048, 1800) if multipart else (96, 96)
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend(((x * 17 + index * 29) & 255,
                         (y * 31 + index * 47) & 255,
                         ((x ^ y) * 13 + index * 61) & 255))
    level = 0 if multipart else 6
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    marker = png_chunk(b"tEXt", f"pics2text-load-index={index}-{uuid.uuid4()}".encode())
    return (b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr) + marker
            + png_chunk(b"IDAT", zlib.compress(bytes(rows), level)) + png_chunk(b"IEND", b""))


def percentile(values: list[float], quantile: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * quantile
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


class Client:
    def __init__(self, base_url: str, username: str, password: str, deadline: float):
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.deadline = deadline
        self.token: str | None = None
        self.created_user = False
        self.project_id: str | None = None

    def remaining_timeout(self) -> float:
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("load test runtime limit reached")
        return min(30.0, remaining)

    def request_json(self, path: str, method: str = "GET", body: Any = None,
                     auth: bool = True) -> tuple[dict[str, Any] | list[Any] | None, Any]:
        headers = {"Content-Type": "application/json"}
        if auth and self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=self.remaining_timeout()) as response:
                raw = response.read()
                return (json.loads(raw) if raw else None), response.headers
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                parsed = json.loads(raw) if raw else {}
            except json.JSONDecodeError:
                parsed = {"message": raw.decode(errors="replace")}
            raise HttpFailure(error.code, parsed, error.headers) from error

    def register_and_login(self) -> None:
        try:
            self.request_json("/api/v1/auth/register", "POST", {
                "username": self.username,
                "password": self.password,
                "displayName": f"LOADTEST {self.username}",
            }, auth=False)
            self.created_user = True
        except HttpFailure as error:
            if error.status != 409:
                raise
        session, _ = self.request_json("/api/v1/auth/login", "POST", {
            "username": self.username, "password": self.password,
        }, auth=False)
        assert isinstance(session, dict)
        self.token = str(session["tokenValue"])

    def create_project(self, run_id: str) -> None:
        project, _ = self.request_json("/api/v1/projects", "POST", {
            "name": f"LOADTEST-{run_id}-{self.username}",
        })
        assert isinstance(project, dict)
        self.project_id = str(project["id"])

    def put(self, signed: dict[str, Any], data: bytes) -> str | None:
        headers: dict[str, str] = {}
        for name, values in signed.get("headers", {}).items():
            if values:
                headers[name] = ",".join(values)
        request = urllib.request.Request(signed["url"], data=data, headers=headers, method="PUT")
        try:
            with urllib.request.urlopen(request, timeout=self.remaining_timeout()) as response:
                response.read()
                return response.headers.get("ETag")
        except urllib.error.HTTPError as error:
            raise HttpFailure(error.code, {"message": error.read().decode(errors="replace")}, error.headers) from error


class ProcessSampler:
    def __init__(self, pid: int | None):
        self.pid = pid
        self.stop_event = threading.Event()
        self.samples: list[dict[str, float]] = []
        self.thread: threading.Thread | None = None

    def start(self) -> None:
        if not self.pid:
            return
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def stop(self) -> dict[str, Any]:
        self.stop_event.set()
        if self.thread:
            self.thread.join(timeout=2)
        return {
            "samples": len(self.samples),
            "rssMiBPeak": round(max((item["rssKiB"] for item in self.samples), default=0) / 1024, 2),
            "cpuPercentPeak": max((item["cpuPercent"] for item in self.samples), default=0),
        }

    def _run(self) -> None:
        while not self.stop_event.wait(0.5):
            result = subprocess.run(
                ["ps", "-o", "rss=", "-o", "%cpu=", "-p", str(self.pid)],
                capture_output=True, text=True, check=False,
            )
            fields = result.stdout.split()
            if len(fields) >= 2:
                self.samples.append({
                    "rssKiB": float(fields[0]), "cpuPercent": float(fields[1]),
                })


def run_one(index: int, client: Client, multipart: bool, retry_attempts: int,
            byte_budget: list[int], budget_lock: threading.Lock) -> dict[str, Any]:
    started = time.monotonic()
    image = synthetic_png(index, multipart)
    with budget_lock:
        byte_budget[0] += len(image)
        if byte_budget[0] > MAX_TOTAL_BYTES:
            raise RuntimeError("generated data exceeded hard 512 MiB limit")
    sha256 = hashlib.sha256(image).hexdigest()
    metrics: dict[str, Any] = {
        "index": index, "bytes": len(image), "modeRequested": "MULTIPART" if multipart else "SINGLE",
        "status": "FAILED", "http429": 0,
    }
    photo_id: str | None = None
    try:
        phase = time.monotonic()
        initiated, _ = client.request_json(f"/api/v1/projects/{client.project_id}/photos/uploads", "POST", {
            "filename": f"load-{index:04d}.png", "contentType": "image/png",
            "byteSize": len(image), "sha256": sha256, "authorizationGranted": True,
        })
        metrics["initiateMs"] = (time.monotonic() - phase) * 1000
        assert isinstance(initiated, dict)
        photo = initiated["photo"]
        photo_id = str(photo["id"])
        metrics["photoId"] = photo_id
        if initiated.get("duplicate"):
            metrics["status"] = "DUPLICATE"
            return metrics

        phase = time.monotonic()
        parts: list[dict[str, Any]] = []
        direct = initiated.get("directUpload")
        if direct:
            client.put(direct, image)
            metrics["modeActual"] = "SINGLE"
        else:
            part_size = int(initiated["partSize"])
            part_count = int(initiated["partCount"])
            metrics["modeActual"] = "MULTIPART"
            for part_index in range(part_count):
                part_number = part_index + 1
                signed, _ = client.request_json(
                    f"/api/v1/projects/{client.project_id}/photos/{photo_id}/uploads/parts/{part_number}/presign",
                    "POST", {})
                assert isinstance(signed, dict)
                etag = client.put(signed, image[part_index * part_size:(part_index + 1) * part_size])
                if not etag:
                    raise RuntimeError("multipart PUT returned no ETag")
                parts.append({"partNumber": part_number, "eTag": etag})
        metrics["uploadMs"] = (time.monotonic() - phase) * 1000

        phase = time.monotonic()
        for attempt in range(1, retry_attempts + 1):
            try:
                completed, _ = client.request_json(
                    f"/api/v1/projects/{client.project_id}/photos/{photo_id}/uploads/complete",
                    "POST", {"parts": parts})
                assert isinstance(completed, dict)
                metrics["status"] = str(completed["status"])
                break
            except HttpFailure as error:
                if error.status != 429 or error.code != "PHOTO_UPLOAD_CAPACITY_EXCEEDED" or attempt == retry_attempts:
                    raise
                metrics["http429"] += 1
                retry_after = float(error.headers.get("Retry-After") or 1)
                time.sleep(min(5.0, max(retry_after, 0.05 * (2 ** (attempt - 1)))))
        metrics["completeMs"] = (time.monotonic() - phase) * 1000
    except Exception as error:  # recorded per item so one failure never aborts the batch
        metrics["error"] = f"{type(error).__name__}: {error}"
        if isinstance(error, HttpFailure):
            metrics["httpStatus"] = error.status
            metrics["errorCode"] = error.code
    finally:
        metrics["totalMs"] = (time.monotonic() - started) * 1000
    return metrics


def summarize(results: list[dict[str, Any]], elapsed: float, sampler: dict[str, Any]) -> dict[str, Any]:
    def phase(name: str) -> dict[str, float | None]:
        values = [float(item[name]) for item in results if name in item]
        return {
            "p50Ms": round(percentile(values, 0.50), 2) if values else None,
            "p95Ms": round(percentile(values, 0.95), 2) if values else None,
            "maxMs": round(max(values), 2) if values else None,
        }
    return {
        "elapsedSeconds": round(elapsed, 3),
        "throughputPerSecond": round(len(results) / elapsed, 3) if elapsed else None,
        "statuses": dict(Counter(str(item.get("status")) for item in results)),
        "http429Attempts": sum(int(item.get("http429", 0)) for item in results),
        "errorCodes": dict(Counter(str(item.get("errorCode")) for item in results if item.get("errorCode"))),
        "bytes": {"total": sum(int(item.get("bytes", 0)) for item in results),
                  "min": min((int(item.get("bytes", 0)) for item in results), default=0),
                  "max": max((int(item.get("bytes", 0)) for item in results), default=0)},
        "latency": {name: phase(name) for name in ("initiateMs", "uploadMs", "completeMs", "totalMs")},
        "apiProcess": sampler,
    }


def local_url(value: str) -> str:
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme not in ("http", "https") or parsed.hostname not in ("localhost", "127.0.0.1", "::1"):
        raise argparse.ArgumentTypeError("load test only permits localhost API URLs")
    return value.rstrip("/")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", type=local_url, default="http://localhost:18080")
    parser.add_argument("--username-prefix", required=True)
    parser.add_argument("--password", default=os.environ.get("PICS2TEXT_LOAD_PASSWORD"))
    parser.add_argument("--users", type=int, default=1, choices=range(1, 9))
    parser.add_argument("--count", type=int, required=True)
    parser.add_argument("--concurrency", type=int, required=True)
    parser.add_argument("--multipart-count", type=int, default=0)
    parser.add_argument("--timeout-seconds", type=int, default=1800)
    parser.add_argument("--complete-retry-attempts", type=int, default=20)
    parser.add_argument("--api-pid", type=int)
    parser.add_argument("--cleanup-photos", action="store_true")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    if not args.password:
        parser.error("--password or PICS2TEXT_LOAD_PASSWORD is required")
    if not 1 <= args.count <= MAX_COUNT:
        parser.error(f"--count must be 1..{MAX_COUNT}")
    if not 1 <= args.concurrency <= MAX_CONCURRENCY:
        parser.error(f"--concurrency must be 1..{MAX_CONCURRENCY}")
    if not 0 <= args.multipart_count <= args.count:
        parser.error("--multipart-count must be between 0 and count")
    if not 1 <= args.timeout_seconds <= MAX_RUNTIME_SECONDS:
        parser.error(f"--timeout-seconds must be 1..{MAX_RUNTIME_SECONDS}")
    return args


def main() -> int:
    args = parse_args()
    run_id = time.strftime("%Y%m%d-%H%M%S")
    deadline = time.monotonic() + args.timeout_seconds
    clients = [Client(args.base_url, f"{args.username_prefix}-{number + 1}", args.password, deadline)
               for number in range(args.users)]
    for client in clients:
        client.register_and_login()
        client.create_project(run_id)

    sampler = ProcessSampler(args.api_pid)
    sampler.start()
    started = time.monotonic()
    byte_budget = [0]
    budget_lock = threading.Lock()
    results: list[dict[str, Any]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(run_one, index, clients[index % len(clients)], index < args.multipart_count,
                                   args.complete_retry_attempts, byte_budget, budget_lock)
                   for index in range(args.count)]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
    elapsed = time.monotonic() - started
    process_metrics = sampler.stop()

    cleanup = {"requested": args.cleanup_photos, "deleted": 0, "failures": []}
    if args.cleanup_photos:
        for item in results:
            photo_id = item.get("photoId")
            if not photo_id:
                continue
            client = clients[int(item["index"]) % len(clients)]
            try:
                client.request_json(f"/api/v1/projects/{client.project_id}/photos/{photo_id}", "DELETE")
                cleanup["deleted"] += 1
            except Exception as error:
                cleanup["failures"].append({"photoId": photo_id, "error": str(error)})

    report = {
        "runId": run_id,
        "limits": {"maxCount": MAX_COUNT, "maxConcurrency": MAX_CONCURRENCY,
                   "maxRuntimeSeconds": MAX_RUNTIME_SECONDS, "maxTotalBytes": MAX_TOTAL_BYTES},
        "configuration": {"baseUrl": args.base_url, "count": args.count, "concurrency": args.concurrency,
                          "multipartCount": args.multipart_count, "users": args.users,
                          "timeoutSeconds": args.timeout_seconds},
        "identities": [{"username": client.username, "created": client.created_user,
                        "projectId": client.project_id} for client in clients],
        "summary": summarize(results, elapsed, process_metrics),
        "cleanup": cleanup,
        "results": sorted(results, key=lambda item: int(item["index"])),
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"report": str(args.report), **report["summary"], "identities": report["identities"]},
                     ensure_ascii=False, indent=2))
    return 0 if report["summary"]["statuses"].get("FAILED", 0) == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
