package org.suai.hashedStorage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class HashedStorageTest {

        @Test
        void CreatedOrUpdatedEventTestBug() {
            HashedStorage hashedStorage = new HashedStorage();
            hashedStorage.CreatedOrUpdatedEvent(Paths.get("123"), false, 0, "");
            hashedStorage.CreatedOrUpdatedEvent(Paths.get("1234"), true, 0, "");
            hashedStorage.CreatedOrUpdatedEvent(Paths.get("1234/123"), false, 0, "");

            // Output of printRootsPaths() method should have 2 lines (only files)
            // replace system out with string builder

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            System.setOut(new PrintStream(outputStream));

            hashedStorage.printRootsPaths();
            String output = outputStream.toString().trim();
            String[] lines = output.split("\n");
            assertEquals(2, lines.length);


            assertEquals("/123", lines[0].trim());
            assertEquals("/1234/123", lines[1].trim());
        }

}