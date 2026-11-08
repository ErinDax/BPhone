package cn.erindax.bphone.phone.chat;

import cn.erindax.bphone.BPhone;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ChatImageStore {
    public static final int CHUNK_SIZE = 24000;
    public static final int MAX_IMAGE_BYTES = 512 * 1024;
    public static final int MAX_CHUNKS = (MAX_IMAGE_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE;
    public static final int MAX_DIMENSION = 1024;
    private static final long UPLOAD_TIMEOUT_MILLIS = 60_000L;
    private static final Pattern ID_PATTERN = Pattern.compile("[0-9a-f\\-]{36}");

    private final Path directory;
    private final Map<UUID, Upload> uploads = new ConcurrentHashMap<>();

    public ChatImageStore(Path directory) {
        this.directory = directory;
    }

    public record CompletedUpload(long conversationId, String peerNumber, byte[] data) {
    }

    private static final class Upload {
        final long uploadId;
        final long conversationId;
        final String peerNumber;
        final byte[][] chunks;
        final long startedAt = System.currentTimeMillis();
        int received;
        int totalBytes;

        Upload(long uploadId, long conversationId, String peerNumber, int count) {
            this.uploadId = uploadId;
            this.conversationId = conversationId;
            this.peerNumber = peerNumber;
            this.chunks = new byte[count][];
        }
    }

    public CompletedUpload acceptChunk(UUID player, long uploadId, long conversationId, String peerNumber,
                                       int index, int count, byte[] data) {
        if (player == null || data == null || count <= 0 || count > MAX_CHUNKS || index < 0 || index >= count
                || data.length > CHUNK_SIZE) {
            uploads.remove(player);
            return null;
        }

        Upload upload = uploads.get(player);
        long now = System.currentTimeMillis();
        if (upload == null || upload.uploadId != uploadId || now - upload.startedAt > UPLOAD_TIMEOUT_MILLIS
                || upload.chunks.length != count) {
            upload = new Upload(uploadId, conversationId, peerNumber, count);
            uploads.put(player, upload);
        }

        if (upload.chunks[index] == null) {
            upload.chunks[index] = data;
            upload.received++;
            upload.totalBytes += data.length;
        }

        if (upload.totalBytes > MAX_IMAGE_BYTES) {
            uploads.remove(player);
            return null;
        }

        if (upload.received < count) {
            return null;
        }

        uploads.remove(player);
        ByteArrayOutputStream output = new ByteArrayOutputStream(upload.totalBytes);
        for (byte[] chunk : upload.chunks) {
            output.writeBytes(chunk);
        }
        return new CompletedUpload(upload.conversationId, upload.peerNumber, output.toByteArray());
    }

    public void forget(UUID player) {
        uploads.remove(player);
    }

    public String save(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_IMAGE_BYTES || !isPng(data)) {
            return null;
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
                return null;
            }
            Files.createDirectories(directory);
            String id = UUID.randomUUID().toString();
            Files.write(directory.resolve(id + ".png"), data);
            return id;
        } catch (IOException exception) {
            BPhone.LOGGER.warn("Failed to store chat image", exception);
            return null;
        }
    }

    public byte[] load(String id) {
        if (!isValidId(id)) {
            return null;
        }

        Path file = directory.resolve(id + ".png");
        try {
            return Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (IOException exception) {
            BPhone.LOGGER.warn("Failed to read chat image {}", id, exception);
            return null;
        }
    }

    public void delete(String id) {
        if (!isValidId(id)) {
            return;
        }

        try {
            Files.deleteIfExists(directory.resolve(id + ".png"));
        } catch (IOException exception) {
            BPhone.LOGGER.debug("Failed to delete chat image {}", id, exception);
        }
    }

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    private static boolean isPng(byte[] data) {
        return data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
    }
}
