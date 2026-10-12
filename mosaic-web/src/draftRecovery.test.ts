import { describe, expect, it } from 'vitest';
import { DraftRecovery, type KeyValueStore, type PendingDraft } from './draftRecovery';

class MemoryStore implements KeyValueStore {
  values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
  removeItem(key: string) { this.values.delete(key); }
}

describe('DraftRecovery', () => {
  it('saves, loads and clears an autosave batch', () => {
    const store = new MemoryStore();
    const recovery = new DraftRecovery(store, 'draft');
    const draft: PendingDraft = { clientBatchId: 'batch', expectedLockVersion: 2,
      commands: [{ type: 'SWAP', slotIndex: 1, secondarySlotIndex: 2 }], savedAt: 'now' };
    recovery.save(draft);
    expect(recovery.load()).toEqual(draft);
    recovery.clear();
    expect(recovery.load()).toBeNull();
  });

  it('ignores damaged local data', () => {
    const store = new MemoryStore();
    store.setItem('draft', '{bad');
    expect(new DraftRecovery(store, 'draft').load()).toBeNull();
  });

  it('discards an unsaved batch when the layout is read-only', () => {
    const store = new MemoryStore();
    const recovery = new DraftRecovery(store, 'draft');
    const draft: PendingDraft = { clientBatchId: 'batch', expectedLockVersion: 2,
      commands: [{ type: 'REPLACE', slotIndex: 497, photoId: 'photo-1' }], savedAt: 'now' };
    recovery.save(draft);
    expect(recovery.loadIfEditable(false)).toBeNull();
    expect(store.values.has('draft')).toBe(false);
    recovery.save(draft);
    expect(recovery.loadIfEditable(true)).toEqual(draft);
  });
});
