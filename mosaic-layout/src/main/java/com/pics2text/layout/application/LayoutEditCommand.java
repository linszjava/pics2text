package com.pics2text.layout.application;

import com.pics2text.layout.domain.CropBox;
import com.pics2text.layout.domain.LayoutCommandType;
import java.util.UUID;

public sealed interface LayoutEditCommand permits LayoutEditCommand.Swap,
        LayoutEditCommand.Replace, LayoutEditCommand.Crop {
    LayoutCommandType type();
    int slotIndex();

    record Swap(int slotIndex, int secondarySlotIndex) implements LayoutEditCommand {
        public Swap {
            if (slotIndex < 0 || secondarySlotIndex < 0 || slotIndex == secondarySlotIndex) {
                throw new IllegalArgumentException("交换操作需要两个不同的有效槽位");
            }
        }
        @Override public LayoutCommandType type() { return LayoutCommandType.SWAP; }
    }

    record Replace(int slotIndex, UUID photoId) implements LayoutEditCommand {
        public Replace {
            if (slotIndex < 0 || photoId == null) throw new IllegalArgumentException("替换操作参数无效");
        }
        @Override public LayoutCommandType type() { return LayoutCommandType.REPLACE; }
    }

    record Crop(int slotIndex, CropBox crop) implements LayoutEditCommand {
        public Crop {
            if (slotIndex < 0 || crop == null) throw new IllegalArgumentException("裁切操作参数无效");
        }
        @Override public LayoutCommandType type() { return LayoutCommandType.CROP; }
    }
}
