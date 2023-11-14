package ca.ibodrov.mica.server;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

public class UuidGenerator {

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    public UUID generate() {
        return generator.generate();
    }
}
