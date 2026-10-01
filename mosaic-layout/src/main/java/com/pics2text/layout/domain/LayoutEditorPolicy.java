package com.pics2text.layout.domain;

public final class LayoutEditorPolicy {
    private LayoutEditorPolicy() { }

    public static void requireEditable(EditableLayoutState state) {
        if (state != EditableLayoutState.DRAFT) {
            throw new IllegalStateException(state == EditableLayoutState.LOCKED
                    ? "布局已锁定，不能继续修改" : "布局已发布，不能继续修改");
        }
    }

    public static EditableLayoutState lock(EditableLayoutState state) {
        requireEditable(state);
        return EditableLayoutState.LOCKED;
    }

    public static EditableLayoutState unlock(EditableLayoutState state) {
        if (state != EditableLayoutState.LOCKED) {
            throw new IllegalStateException("只有已锁定布局可以解锁");
        }
        return EditableLayoutState.DRAFT;
    }

    public static EditableLayoutState publish(EditableLayoutState state) {
        if (state != EditableLayoutState.LOCKED) {
            throw new IllegalStateException("布局必须先锁定才能发布");
        }
        return EditableLayoutState.PUBLISHED;
    }
}
