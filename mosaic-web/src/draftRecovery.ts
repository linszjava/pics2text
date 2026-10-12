import type { EditCommand } from './types';

export interface PendingDraft {
  clientBatchId: string;
  expectedLockVersion: number;
  commands: EditCommand[];
  savedAt: string;
}

export interface KeyValueStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export class DraftRecovery {
  constructor(private readonly store: KeyValueStore, private readonly key: string) {}

  save(value: PendingDraft): void {
    this.store.setItem(this.key, JSON.stringify(value));
  }

  load(): PendingDraft | null {
    const raw = this.store.getItem(this.key);
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw) as PendingDraft;
      return parsed.clientBatchId && Array.isArray(parsed.commands) ? parsed : null;
    } catch {
      return null;
    }
  }

  loadIfEditable(editable: boolean): PendingDraft | null {
    if (!editable) {
      this.clear();
      return null;
    }
    return this.load();
  }

  clear(): void { this.store.removeItem(this.key); }
}
