package com.pics2text.layout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pics2text.layout.application.LayoutEditCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayoutEditorPolicyTest {
    @Test
    void onlyDraftLayoutsCanBeEdited() {
        LayoutEditorPolicy.requireEditable(EditableLayoutState.DRAFT);
        assertThatThrownBy(() -> LayoutEditorPolicy.requireEditable(EditableLayoutState.LOCKED))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("已锁定");
        assertThatThrownBy(() -> LayoutEditorPolicy.requireEditable(EditableLayoutState.PUBLISHED))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("已发布");
    }

    @Test
    void stateTransitionsRequireLockBeforePublish() {
        assertThat(LayoutEditorPolicy.lock(EditableLayoutState.DRAFT)).isEqualTo(EditableLayoutState.LOCKED);
        assertThat(LayoutEditorPolicy.unlock(EditableLayoutState.LOCKED)).isEqualTo(EditableLayoutState.DRAFT);
        assertThat(LayoutEditorPolicy.publish(EditableLayoutState.LOCKED)).isEqualTo(EditableLayoutState.PUBLISHED);
        assertThatThrownBy(() -> LayoutEditorPolicy.publish(EditableLayoutState.DRAFT))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("先锁定");
    }

    @Test
    void cropAndCommandsRejectInvalidArguments() {
        assertThatThrownBy(() -> new CropBox(0.8, 0, 0.3, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LayoutEditCommand.Swap(1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LayoutEditCommand.Replace(0, null))
                .isInstanceOf(IllegalArgumentException.class);
        LayoutEditCommand command = new LayoutEditCommand.Crop(3, new CropBox(0.1, 0.2, 0.7, 0.6));
        assertThat(command.type()).isEqualTo(LayoutCommandType.CROP);
        assertThat(new LayoutEditCommand.Replace(2, UUID.randomUUID()).slotIndex()).isEqualTo(2);
    }
}
