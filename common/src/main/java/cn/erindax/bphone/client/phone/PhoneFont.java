package cn.erindax.bphone.client.phone;

import cn.erindax.bphone.BPhone;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public final class PhoneFont {
    private static final float OVERSAMPLE = 6.0F;
    private static final int PIXEL_SIZE = 45;
    private static final int CANVAS_SIZE = 96;
    private static final int CANVAS_ORIGIN_X = 16;
    private static final int CANVAS_BASELINE_Y = 66;
    private static final int PAGE_SIZE = 1024;
    private static final int GUTTER = 2;
    private static final float BOLD_OFFSET = 0.36F;
    private static final float SHADOW_OFFSET = 0.48F;
    private static final String[] PREFERRED_FAMILIES = {
            "Microsoft YaHei UI",
            "Microsoft YaHei",
            "HarmonyOS Sans SC",
            "MiSans",
            "PingFang SC",
            "Hiragino Sans GB",
            "Noto Sans CJK SC",
            "Source Han Sans SC",
            "WenQuanYi Micro Hei",
            "Segoe UI",
            "Leelawadee UI",
            "Segoe UI Symbol",
            "Segoe UI Emoji",
            java.awt.Font.SANS_SERIF,
            java.awt.Font.DIALOG
    };

    private static Font instance;
    private static FontSet fontSet;
    private static SystemGlyphProvider provider;
    private static boolean failed;

    private PhoneFont() {
    }

    public static Font get() {
        Minecraft minecraft = Minecraft.getInstance();
        if (failed || minecraft == null) {
            return minecraft == null ? null : minecraft.font;
        }
        if (instance == null) {
            try {
                provider = new SystemGlyphProvider();
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, "modern_ui");
                fontSet = new FontSet(minecraft.getTextureManager(), id);
                fontSet.reload(List.of(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)), Set.of());
                instance = new Font(location -> fontSet, false);
            } catch (Throwable throwable) {
                failed = true;
                BPhone.LOGGER.warn("Unable to initialize smooth UI font, falling back to vanilla font", throwable);
                return minecraft.font;
            }
        }
        return instance;
    }

    public static void shutdown() {
        if (fontSet != null) {
            fontSet.close();
            fontSet = null;
        }
        if (provider != null) {
            provider.close();
            provider = null;
        }
        instance = null;
    }

    private static final class LinearAtlasTexture extends DynamicTexture {
        LinearAtlasTexture(NativeImage image) {
            super(image);
            super.setFilter(true, false);
        }

        @Override
        public void setFilter(boolean blur, boolean mipmap) {
            super.setFilter(true, false);
        }
    }

    private static final class AtlasPage {
        final ResourceLocation id;
        final LinearAtlasTexture texture;
        final GlyphRenderTypes renderTypes;
        int cursorX = GUTTER;
        int cursorY = GUTTER;
        int rowHeight;

        AtlasPage(int index) {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, PAGE_SIZE, PAGE_SIZE, false);
            image.fillRect(0, 0, PAGE_SIZE, PAGE_SIZE, 0x00FFFFFF);
            this.id = ResourceLocation.fromNamespaceAndPath(BPhone.MOD_ID, "dynamic/modern_font_" + index);
            this.texture = new LinearAtlasTexture(image);
            Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
            this.renderTypes = GlyphRenderTypes.createForColorTexture(this.id);
        }

        int[] allocate(int width, int height) {
            if (cursorX + width + GUTTER > PAGE_SIZE) {
                cursorX = GUTTER;
                cursorY += rowHeight + GUTTER;
                rowHeight = 0;
            }
            if (cursorY + height + GUTTER > PAGE_SIZE) {
                return null;
            }
            int x = cursorX;
            int y = cursorY;
            cursorX += width + GUTTER;
            rowHeight = Math.max(rowHeight, height);
            return new int[] {x, y};
        }

        void close() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.getTextureManager().release(id);
            }
            texture.close();
        }
    }

    private static final class EmptyBakedGlyph extends BakedGlyph {
        EmptyBakedGlyph(GlyphRenderTypes renderTypes) {
            super(renderTypes, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        }

        @Override
        public void render(boolean italic, float x, float y, Matrix4f matrix, VertexConsumer buffer,
                           float red, float green, float blue, float alpha, int packedLight) {
        }
    }

    private static final class SystemGlyphProvider implements GlyphProvider {
        private final List<java.awt.Font> fontChain = new ArrayList<>();
        private final BufferedImage scratch = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        private final Graphics2D graphics;
        private final float baselineGui;
        private final Int2ObjectMap<GlyphInfo> cache = new Int2ObjectOpenHashMap<>();
        private final List<AtlasPage> pages = new ArrayList<>();
        private EmptyBakedGlyph emptyBaked;

        SystemGlyphProvider() {
            this.graphics = scratch.createGraphics();
            this.graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            this.graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            this.graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            this.graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            this.graphics.setBackground(new Color(0, 0, 0, 0));

            Set<String> available = new HashSet<>();
            for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT)) {
                available.add(family.toLowerCase(Locale.ROOT));
            }
            for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                available.add(family.toLowerCase(Locale.ROOT));
            }
            Set<String> added = new HashSet<>();
            for (String candidate : PREFERRED_FAMILIES) {
                String key = candidate.toLowerCase(Locale.ROOT);
                boolean logical = java.awt.Font.SANS_SERIF.equalsIgnoreCase(candidate)
                        || java.awt.Font.DIALOG.equalsIgnoreCase(candidate);
                if ((logical || available.contains(key)) && added.add(key)) {
                    fontChain.add(new java.awt.Font(candidate, java.awt.Font.PLAIN, PIXEL_SIZE));
                }
            }
            if (fontChain.isEmpty()) {
                fontChain.add(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, PIXEL_SIZE));
            }

            java.awt.Font primary = fontChain.get(0);
            FontRenderContext frc = graphics.getFontRenderContext();
            String probe = primary.canDisplay('中') ? "H0中" : "H0x";
            Rectangle2D visual = primary.createGlyphVector(frc, probe).getVisualBounds();
            double visualCenter = (visual.getMinY() + visual.getMaxY()) * 0.5D;
            this.baselineGui = 4.0F - (float) (visualCenter / OVERSAMPLE);
        }

        @Override
        public synchronized GlyphInfo getGlyph(int codePoint) {
            return cache.computeIfAbsent(codePoint, this::rasterize);
        }

        @Override
        public IntSet getSupportedGlyphs() {
            IntSet set = new IntOpenHashSet(96);
            for (int ch = 32; ch <= 126; ch++) {
                set.add(ch);
            }
            return set;
        }

        @Override
        public synchronized void close() {
            cache.clear();
            for (AtlasPage page : pages) {
                page.close();
            }
            pages.clear();
            emptyBaked = null;
            graphics.dispose();
        }

        private GlyphInfo rasterize(int codePoint) {
            if (isZeroWidth(codePoint)) {
                return whitespace(0.0F);
            }
            if (codePoint == '\t') {
                return whitespace(8.0F);
            }
            if (Character.isWhitespace(codePoint) || codePoint == 0x00A0) {
                java.awt.Font spaceFont = selectFont(' ');
                graphics.setFont(spaceFont);
                float advance = Math.max(2.0F, (float) spaceFont.getStringBounds(" ", graphics.getFontRenderContext()).getWidth() / OVERSAMPLE);
                return whitespace(advance);
            }

            java.awt.Font chosen = selectFont(codePoint);
            if (chosen == null) {
                return null;
            }

            String text = new String(Character.toChars(codePoint));
            graphics.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
            graphics.setFont(chosen);
            graphics.setColor(Color.WHITE);
            graphics.drawString(text, CANVAS_ORIGIN_X, CANVAS_BASELINE_Y);

            FontMetrics metrics = graphics.getFontMetrics(chosen);
            double rawAdvance = chosen.getStringBounds(text, graphics.getFontRenderContext()).getWidth();
            if (rawAdvance <= 0.01D) {
                rawAdvance = metrics.stringWidth(text);
            }
            float advance = Math.max(1.0F, (float) (rawAdvance / OVERSAMPLE) + 0.18F);

            int minX = CANVAS_SIZE;
            int minY = CANVAS_SIZE;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < CANVAS_SIZE; y++) {
                for (int x = 0; x < CANVAS_SIZE; x++) {
                    int alpha = (scratch.getRGB(x, y) >>> 24) & 0xFF;
                    if (alpha > 3) {
                        if (x < minX) minX = x;
                        if (y < minY) minY = y;
                        if (x > maxX) maxX = x;
                        if (y > maxY) maxY = y;
                    }
                }
            }

            if (maxX < minX || maxY < minY) {
                return whitespace(advance);
            }

            int width = maxX - minX + 1;
            int height = maxY - minY + 1;
            byte[] alphas = new byte[width * height];
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int raw = (scratch.getRGB(minX + col, minY + row) >>> 24) & 0xFF;
                    int boosted = raw == 0 ? 0 : Math.min(255, Math.round((float) Math.pow(raw / 255.0D, 0.78D) * 255.0F));
                    alphas[row * width + col] = (byte) boosted;
                }
            }

            float left = (minX - CANVAS_ORIGIN_X) / OVERSAMPLE;
            float right = (maxX + 1 - CANVAS_ORIGIN_X) / OVERSAMPLE;
            float up = baselineGui - (CANVAS_BASELINE_Y - minY) / OVERSAMPLE;
            float down = baselineGui + (maxY + 1 - CANVAS_BASELINE_Y) / OVERSAMPLE;
            return new RasterGlyph(advance, width, height, alphas, left, right, up, down);
        }

        private GlyphInfo whitespace(float advance) {
            return new GlyphInfo() {
                @Override
                public float getAdvance() {
                    return advance;
                }

                @Override
                public float getBoldOffset() {
                    return BOLD_OFFSET;
                }

                @Override
                public float getShadowOffset() {
                    return SHADOW_OFFSET;
                }

                @Override
                public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> stitcher) {
                    return emptyGlyph();
                }
            };
        }

        private synchronized BakedGlyph emptyGlyph() {
            if (emptyBaked == null) {
                AtlasPage page = pages.isEmpty() ? newPage() : pages.get(0);
                emptyBaked = new EmptyBakedGlyph(page.renderTypes);
            }
            return emptyBaked;
        }

        private AtlasPage newPage() {
            AtlasPage page = new AtlasPage(pages.size());
            pages.add(page);
            return page;
        }

        synchronized BakedGlyph uploadGlyph(int width, int height, byte[] alphas,
                                            float left, float right, float up, float down) {
            AtlasPage target = null;
            int[] slot = null;
            for (AtlasPage page : pages) {
                slot = page.allocate(width, height);
                if (slot != null) {
                    target = page;
                    break;
                }
            }
            if (target == null) {
                target = newPage();
                slot = target.allocate(width, height);
            }
            if (slot == null) {
                return emptyGlyph();
            }

            int x0 = slot[0];
            int y0 = slot[1];
            NativeImage pixels = target.texture.getPixels();
            if (pixels != null) {
                for (int row = 0; row < height; row++) {
                    for (int col = 0; col < width; col++) {
                        int a = alphas[row * width + col] & 0xFF;
                        pixels.setPixelRGBA(x0 + col, y0 + row, (a << 24) | 0x00FFFFFF);
                    }
                }
                target.texture.bind();
                pixels.upload(0, x0, y0, x0, y0, width, height, true, false, false, false);
            }

            float u0 = x0 / (float) PAGE_SIZE;
            float u1 = (x0 + width) / (float) PAGE_SIZE;
            float v0 = y0 / (float) PAGE_SIZE;
            float v1 = (y0 + height) / (float) PAGE_SIZE;
            return new BakedGlyph(target.renderTypes, u0, u1, v0, v1, left, right, up, down);
        }

        private java.awt.Font selectFont(int codePoint) {
            for (java.awt.Font candidate : fontChain) {
                if (candidate.canDisplay(codePoint)) {
                    return candidate;
                }
            }
            return fontChain.get(fontChain.size() - 1);
        }

        private static boolean isZeroWidth(int codePoint) {
            return Character.isISOControl(codePoint)
                    || codePoint == 0x200B
                    || codePoint == 0x200C
                    || codePoint == 0x200D
                    || codePoint == 0xFEFF
                    || (codePoint >= 0xFE00 && codePoint <= 0xFE0F);
        }

        private final class RasterGlyph implements GlyphInfo {
            private final float advance;
            private final int width;
            private final int height;
            private final byte[] alphas;
            private final float left;
            private final float right;
            private final float up;
            private final float down;

            RasterGlyph(float advance, int width, int height, byte[] alphas,
                        float left, float right, float up, float down) {
                this.advance = advance;
                this.width = width;
                this.height = height;
                this.alphas = alphas;
                this.left = left;
                this.right = right;
                this.up = up;
                this.down = down;
            }

            @Override
            public float getAdvance() {
                return advance;
            }

            @Override
            public float getBoldOffset() {
                return BOLD_OFFSET;
            }

            @Override
            public float getShadowOffset() {
                return SHADOW_OFFSET;
            }

            @Override
            public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> stitcher) {
                return uploadGlyph(width, height, alphas, left, right, up, down);
            }
        }
    }
}
