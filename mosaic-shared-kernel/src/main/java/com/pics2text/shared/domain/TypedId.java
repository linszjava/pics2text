package com.pics2text.shared.domain;

import java.io.Serializable;
import java.util.UUID;

public interface TypedId extends Serializable {

    UUID value();
}

