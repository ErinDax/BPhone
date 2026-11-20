package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.BPhone;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PhoneWallpaperImport {
    private static final int MAX_DIMENSION = 1024;
    private static final long MAX_SOURCE_PIXELS = 100_000_000L;
    private static final String[] FILE_PATTERNS = {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"};
    private static final int JPEG_START = 0xFFD8;
    private static final int JPEG_SCAN = 0xFFDA;
    private static final int JPEG_APP1 = 0xFFE1;
    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};
    private static final int TIFF_LITTLE_ENDIAN = 0x4949;
    private static final int EXIF_ORIENTATION = 0x0112;
    private static final Executor WORKER = Thread.ofPlatform().daemon().name("BPhone-Wallpaper-Import")::start;
    private static boolean busy;

    private PhoneWallpaperImport() {
    }

    static boolean isBusy() {
        return busy;
    }

    static Supplier<Path> dialog(String title, String filterName) {
        return () -> chooseFile(title, filterName);
    }

    static void start(Supplier<Path> source, Consumer<Optional<byte[]>> onPicked) {
        if (busy) {
            return;
        }

        busy = true;
        CompletableFuture
                .supplyAsync(() -> Optional.ofNullable(source.get()).map(PhoneWallpaperImport::normalize), WORKER)
                .exceptionally(error -> {
                    BPhone.LOGGER.warn("Unable to import wallpaper", error);
                    return Optional.of(Optional.empty());
                })
                .thenAcceptAsync(picked -> {
                    busy = false;
                    picked.ifPresent(onPicked);
                }, Minecraft.getInstance())
                .exceptionally(error -> {
                    BPhone.LOGGER.warn("Unable to apply imported wallpaper", error);
                    return null;
                });
    }

    private static Path chooseFile(String title, String filterName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer patterns = stack.mallocPointer(FILE_PATTERNS.length);
            for (String pattern : FILE_PATTERNS) {
                patterns.put(stack.UTF8(pattern));
            }
            patterns.flip();
            String selected = TinyFileDialogs.tinyfd_openFileDialog(title, null, patterns, filterName, false);
            return selected == null || selected.isBlank() ? null : Path.of(selected);
        }
    }

    private static Optional<byte[]> normalize(Path file) {
        try {
            BufferedImage source = file.toFile().isFile() ? decode(file) : null;
            return source == null ? Optional.empty() : Optional.of(encode(fit(source)));
        } catch (Exception exception) {
            BPhone.LOGGER.debug("Unable to read wallpaper image {}", file, exception);
            return Optional.empty();
        }
    }

    private static BufferedImage decode(Path file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = input == null ? Collections.emptyIterator() : ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_SOURCE_PIXELS) {
                    return null;
                }

                int step = Math.max(1, Math.max(width, height) / (2 * MAX_DIMENSION));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return orient(reader.read(0, param), readOrientation(file));
            } finally {
                reader.dispose();
            }
        }
    }

    private static int readOrientation(Path file) {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readUnsignedShort() != JPEG_START) {
                return 1;
            }

            while (true) {
                int marker = input.readUnsignedShort();
                int length = input.readUnsignedShort() - 2;
                if (marker >> 8 != 0xFF || marker == JPEG_SCAN || length < 0) {
                    return 1;
                }

                byte[] segment = input.readNBytes(length);
                int orientation = marker == JPEG_APP1 ? exifOrientation(segment) : 0;
                if (orientation != 0) {
                    return orientation;
                }
            }
        } catch (IOException | IndexOutOfBoundsException exception) {
            return 1;
        }
    }

    private static int exifOrientation(byte[] segment) {
        int header = EXIF_HEADER.length;
        if (segment.length < header || !Arrays.equals(segment, 0, header, EXIF_HEADER, 0, header)) {
            return 0;
        }

        ByteBuffer tiff = ByteBuffer.wrap(segment, header, segment.length - header).slice();
        tiff.order(tiff.getShort(0) == TIFF_LITTLE_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        int directory = tiff.getInt(4);
        int entries = Short.toUnsignedInt(tiff.getShort(directory));
        for (int index = 0; index < entries; index++) {
            int entry = directory + 2 + index * 12;
            if (Short.toUnsignedInt(tiff.getShort(entry)) == EXIF_ORIENTATION) {
                int orientation = Short.toUnsignedInt(tiff.getShort(entry + 8));
                return orientation >= 1 && orientation <= 8 ? orientation : 0;
            }
        }
        return 0;
    }

    private static BufferedImage orient(BufferedImage source, int orientation) {
        int width = source.getWidth();
        int height = source.getHeight();
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> null;
        };
        if (transform == null) {
            return source;
        }

        boolean turned = orientation >= 5;
        BufferedImage target = new BufferedImage(turned ? height : width, turned ? width : height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(source, transform, null);
        graphics.dispose();
        return target;
    }

    private static BufferedImage fit(BufferedImage source) {
        float factor = Math.min(1.0F, (float) MAX_DIMENSION / Math.max(source.getWidth(), source.getHeight()));
        int targetWidth = Math.max(1, Math.round(source.getWidth() * factor));
        int targetHeight = Math.max(1, Math.round(source.getHeight() * factor));
        BufferedImage current = source;
        do {
            current = redraw(current, Math.max(targetWidth, current.getWidth() / 2),
                    Math.max(targetHeight, current.getHeight() / 2));
        } while (current.getWidth() != targetWidth || current.getHeight() != targetHeight);
        return current;
    }

    private static BufferedImage redraw(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = new MemoryCacheImageOutputStream(output)) {
            if (!ImageIO.write(image, "png", stream)) {
                throw new IOException("No PNG writer available");
            }
        }
        return output.toByteArray();
    }
}