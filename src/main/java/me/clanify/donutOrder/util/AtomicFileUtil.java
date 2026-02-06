package me.clanify.donutOrder.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public class AtomicFileUtil {

    /**
     * Perform an atomic write to a file.
     * 
     * @param file   The target file.
     * @param writer A consumer that writes to the provided FileOutputStream.
     * @throws IOException If I/O error occurs.
     */
    public static void write(File file, Consumer<FileOutputStream> writer) throws IOException {
        File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            writer.accept(fos);
            fos.getFD().sync(); // Ensure data is flushed to disk
        } catch (Throwable e) {
            // If writing fails, clean up temp file
            if (tempFile.exists()) {
                tempFile.delete();
            }
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Atomic write failed for " + file.getName(), e);
        }

        try {
            // atomic move
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Fallback for Windows if ATOMIC_MOVE fails or file locked?
            // Try standard replace
            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                if (tempFile.exists())
                    tempFile.delete();
                throw new IOException("Failed to move temp file to target: " + file.getName(), ex);
            }
        }
    }
}
