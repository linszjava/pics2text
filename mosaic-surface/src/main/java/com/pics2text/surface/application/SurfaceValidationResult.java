package com.pics2text.surface.application;

import java.util.List;

public record SurfaceValidationResult(boolean valid, List<SurfaceViolation> violations) {
    public SurfaceValidationResult { violations = List.copyOf(violations); }
}
