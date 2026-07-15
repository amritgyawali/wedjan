package com.wedjan.api.common;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/** Time-ordered UUIDv7 ids for every entity (index-friendly, sortable). */
public final class Uuidv7 {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private Uuidv7() {}

    public static UUID next() {
        return GENERATOR.generate();
    }
}
