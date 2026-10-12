export const MAX_CONCURRENT_UPLOADS = 3;

export type UploadBatchOutcome = 'SUCCEEDED' | 'DUPLICATE' | 'FAILED';

export type UploadBatchSummary = {
  succeeded: number;
  duplicates: number;
  failed: number;
  kind: 'success' | 'warning' | 'error';
  content: string;
};

export async function retryWithBackoff<T>(
  task: () => Promise<T>,
  retryDelay: (error: unknown) => number | null,
  options: { maxAttempts: number; baseDelayMs: number; maxDelayMs: number },
  sleep: (milliseconds: number) => Promise<void> = (milliseconds) =>
    new Promise((resolve) => window.setTimeout(resolve, milliseconds)),
): Promise<T> {
  for (let attempt = 1; ; attempt += 1) {
    try {
      return await task();
    } catch (error) {
      const requestedDelay = retryDelay(error);
      if (requestedDelay == null || attempt >= options.maxAttempts) throw error;
      const exponentialDelay = options.baseDelayMs * (2 ** (attempt - 1));
      await sleep(Math.min(options.maxDelayMs, Math.max(requestedDelay, exponentialDelay)));
    }
  }
}

export async function runBoundedQueue<T, R>(
  items: readonly T[],
  concurrency: number,
  task: (item: T, index: number) => Promise<R>,
): Promise<PromiseSettledResult<R>[]> {
  if (!Number.isInteger(concurrency) || concurrency < 1) {
    throw new RangeError('concurrency must be a positive integer');
  }

  const results = new Array<PromiseSettledResult<R>>(items.length);
  let nextIndex = 0;
  const worker = async () => {
    while (nextIndex < items.length) {
      const index = nextIndex;
      nextIndex += 1;
      try {
        results[index] = { status: 'fulfilled', value: await task(items[index], index) };
      } catch (reason) {
        results[index] = { status: 'rejected', reason };
      }
    }
  };

  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, worker));
  return results;
}

export function summarizeUploadBatch(outcomes: readonly UploadBatchOutcome[]): UploadBatchSummary {
  const succeeded = outcomes.filter((outcome) => outcome === 'SUCCEEDED').length;
  const duplicates = outcomes.filter((outcome) => outcome === 'DUPLICATE').length;
  const failed = outcomes.filter((outcome) => outcome === 'FAILED').length;
  const kind = failed === outcomes.length && failed > 0 ? 'error' : failed > 0 ? 'warning' : 'success';
  return {
    succeeded,
    duplicates,
    failed,
    kind,
    content: `批量上传处理完成：成功 ${succeeded} 张，重复 ${duplicates} 张，失败 ${failed} 张`,
  };
}
