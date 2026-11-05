#!/usr/bin/env python3
"""Run bounded, opt-in load scenarios and retain reports without payloads or credentials."""
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs' / 'performance'
OUTPUT.mkdir(parents=True, exist_ok=True)
SCENARIOS = [(10000, 1024, 1), (10000, 1024, 4), (1000, 65536, 1), (1000, 65536, 4)]


def main():
    (ROOT / 'backend' / 'target').mkdir(parents=True, exist_ok=True)
    for count, size, concurrency in SCENARIOS:
        label = f'{count}-{size}-c{concurrency}'
        command = ['./mvnw', '-B', '-ntp', '-Pbenchmark', f'-Dbench.messages={count}',
                   f'-Dbench.bytes={size}', f'-Dbench.concurrency={concurrency}', 'verify']
        print('Starting', label, flush=True)
        # Detailed Java logs are disposable; only structured measurements enter docs.
        with (ROOT / 'backend' / 'target' / f'run-{label}.log').open('w') as log:
            process = subprocess.Popen(command, cwd=ROOT / 'backend', stdout=log, stderr=subprocess.STDOUT)
            telemetry = []
            deadline = time.monotonic() + 420
            try:
                while process.poll() is None:
                    if time.monotonic() > deadline:
                        raise TimeoutError(f'Benchmark timeout: {label}')
                    result = subprocess.run(['docker', 'stats', '--no-stream', '--format', '{{json .}}',
                                             'bank-messaging-mq-1', 'bank-messaging-postgres-1'],
                                            capture_output=True, text=True, timeout=15)
                    if result.returncode != 0:
                        raise RuntimeError('Docker stats failed')
                    telemetry.append({'sampledAt': time.time(), 'containers': [json.loads(line) for line in result.stdout.splitlines() if line]})
                    time.sleep(1)
                if process.returncode != 0:
                    raise RuntimeError(f'Benchmark failed; see backend/target/run-{label}.log')
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
        report = json.loads((ROOT / 'backend' / 'target' / f'benchmark-{label}.json').read_text())
        report['dockerSamples'] = telemetry
        (OUTPUT / f'benchmark-{label}.json').write_text(json.dumps(report, indent=2) + '\n')
        print('Completed', label, round(report['messagesPerSecond'], 1), 'msg/s', flush=True)


if __name__ == '__main__':
    main()
