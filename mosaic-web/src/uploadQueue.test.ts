import { describe, expect, it } from 'vitest';
import { MAX_CONCURRENT_UPLOADS, retryWithBackoff, runBoundedQueue, summarizeUploadBatch } from './uploadQueue';

type Deferred = { promise: Promise<void>; resolve: () => void };

function deferred(): Deferred {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => { resolve = done; });
  return { promise, resolve };
}

async function flushMicrotasks() {
  await Promise.resolve();
  await Promise.resolve();
}

describe('runBoundedQueue', () => {
  it('processes 500 items exactly once without exceeding the upload limit', async () => {
    const items = Array.from({ length: 500 }, (_, index) => index);
    const executions = new Uint16Array(items.length);
    let active = 0;
    let peak = 0;

    const results = await runBoundedQueue(items, MAX_CONCURRENT_UPLOADS, async (item) => {
      executions[item] += 1;
      active += 1;
      peak = Math.max(peak, active);
      await Promise.resolve();
      active -= 1;
      return item;
    });

    expect(results).toHaveLength(500);
    expect(results.every((result) => result.status === 'fulfilled')).toBe(true);
    expect([...executions].every((count) => count === 1)).toBe(true);
    expect(peak).toBe(MAX_CONCURRENT_UPLOADS);
  });

  it('isolates failures across 1000 items and preserves exact batch counts', async () => {
    const items = Array.from({ length: 1000 }, (_, index) => index);
    const executions = new Uint16Array(items.length);
    let active = 0;
    let peak = 0;

    const results = await runBoundedQueue(items, MAX_CONCURRENT_UPLOADS, async (item) => {
      executions[item] += 1;
      active += 1;
      peak = Math.max(peak, active);
      await Promise.resolve();
      active -= 1;
      if (item % 125 === 0) throw new Error(`failed-${item}`);
      return item % 10 === 0 ? 'DUPLICATE' as const : 'SUCCEEDED' as const;
    });
    const outcomes = results.map((result) => result.status === 'fulfilled' ? result.value : 'FAILED');

    expect(results).toHaveLength(1000);
    expect([...executions].every((count) => count === 1)).toBe(true);
    expect(peak).toBe(MAX_CONCURRENT_UPLOADS);
    expect(results.filter((result) => result.status === 'rejected')).toHaveLength(8);
    expect(summarizeUploadBatch(outcomes)).toEqual({
      succeeded: 896,
      duplicates: 96,
      failed: 8,
      kind: 'warning',
      content: '批量上传处理完成：成功 896 张，重复 96 张，失败 8 张',
    });
  });

  it('starts no more than the configured number and fills a slot as soon as one finishes', async () => {
    const gates = Array.from({ length: 5 }, deferred);
    const started: number[] = [];
    let active = 0;
    let peak = 0;
    const running = runBoundedQueue([0, 1, 2, 3, 4], MAX_CONCURRENT_UPLOADS, async (item) => {
      started.push(item);
      active += 1;
      peak = Math.max(peak, active);
      await gates[item].promise;
      active -= 1;
      return item;
    });

    await flushMicrotasks();
    expect(started).toEqual([0, 1, 2]);
    expect(peak).toBe(MAX_CONCURRENT_UPLOADS);

    gates[1].resolve();
    await flushMicrotasks();
    expect(started).toEqual([0, 1, 2, 3]);

    gates[0].resolve(); gates[2].resolve(); gates[3].resolve();
    await flushMicrotasks();
    expect(started).toEqual([0, 1, 2, 3, 4]);
    gates[4].resolve();
    await expect(running).resolves.toEqual([0, 1, 2, 3, 4].map((value) => ({ status: 'fulfilled', value })));
  });

  it('isolates a failed item and continues with the rest of the queue', async () => {
    const visited: number[] = [];
    const results = await runBoundedQueue([0, 1, 2, 3], 2, async (item) => {
      visited.push(item);
      if (item === 1) throw new Error('upload failed');
      return item * 10;
    });

    expect(visited).toHaveLength(4);
    expect(results[1]).toMatchObject({ status: 'rejected' });
    expect(results[3]).toEqual({ status: 'fulfilled', value: 30 });
  });

  it('rejects invalid concurrency limits', async () => {
    await expect(runBoundedQueue([1], 0, async (item) => item)).rejects.toThrow(RangeError);
    await expect(runBoundedQueue([1], 1.5, async (item) => item)).rejects.toThrow(RangeError);
  });
});

describe('summarizeUploadBatch', () => {
  it('reports mixed results as a warning with exact counts', () => {
    expect(summarizeUploadBatch(['SUCCEEDED', 'DUPLICATE', 'FAILED', 'SUCCEEDED'])).toEqual({
      succeeded: 2,
      duplicates: 1,
      failed: 1,
      kind: 'warning',
      content: '批量上传处理完成：成功 2 张，重复 1 张，失败 1 张',
    });
  });

  it('reports an all-failed batch as an error instead of success', () => {
    expect(summarizeUploadBatch(['FAILED', 'FAILED'])).toMatchObject({ kind: 'error', failed: 2 });
  });
});

describe('retryWithBackoff', () => {
  it('retries only up to the configured bound and honors the larger retry delay', async () => {
    let attempts = 0;
    const delays: number[] = [];
    const result = await retryWithBackoff(async () => {
      attempts += 1;
      if (attempts < 3) throw new Error('busy');
      return 'ok';
    }, () => 750, { maxAttempts: 4, baseDelayMs: 100, maxDelayMs: 1000 }, async (delay) => {
      delays.push(delay);
    });

    expect(result).toBe('ok');
    expect(attempts).toBe(3);
    expect(delays).toEqual([750, 750]);
  });

  it('does not retry non-retryable failures', async () => {
    let attempts = 0;
    await expect(retryWithBackoff(async () => {
      attempts += 1;
      throw new Error('invalid image');
    }, () => null, { maxAttempts: 8, baseDelayMs: 100, maxDelayMs: 1000 }, async () => undefined))
      .rejects.toThrow('invalid image');
    expect(attempts).toBe(1);
  });
});
