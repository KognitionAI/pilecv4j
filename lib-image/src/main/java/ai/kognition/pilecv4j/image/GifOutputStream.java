/***********************************************************************
 * Legacy Film to DVD Project
 * Copyright (C) 2005 James F. Carroll
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 ****************************************************************************/

package ai.kognition.pilecv4j.image;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class GifOutputStream extends FilterOutputStream {
    public static final int ORIGINAL_COLOR = 0;
    public static final int BLACK_AND_WHITE = 1;
    public static final int GRAYSCALE_16 = 2;
    public static final int GRAYSCALE_256 = 3;
    public static final int STANDARD_16_COLORS = 4;
    public static final int STANDARD_256_COLORS = 5;

    public static final int NO_ERROR = 0;
    public static final int IMAGE_LOAD_FAILED = 1;
    public static final int TOO_MANY_COLORS = 2;
    public static final int INVALID_COLOR_MODE = 3;

    protected static final int BLACK_INDEX = 0;
    protected static final int WHITE_INDEX = 1;

    protected static final int[] standard16 = {
        0x000000,
        0xFF0000,0x00FF00,0x0000FF,
        0x00FFFF,0xFF00FF,0xFFFF00,
        0x800000,0x008000,0x000080,
        0x008080,0x800080,0x808000,
        0x808080,0xC0C0C0,
        0xFFFFFF
    };

    protected static final int[] standard256 = {
        // Black, index 0
        0x000000,
        // Shades of gray w/o 0x33 multiples, starting at index 1
        0x111111,0x222222,0x444444,0x555555,0x777777,0x888888,
        0xAAAAAA,0xBBBBBB,0xDDDDDD,0xEEEEEE,
        // Shades of blue w/o 0x33 multiples, starting at index 11
        0x000011,0x000022,0x000044,0x000055,0x000077,0x000088,
        0x0000AA,0x0000BB,0x0000DD,0x0000EE,
        // Shades of green w/o 0x33 multiples, starting at index 21
        0x001100,0x002200,0x004400,0x005500,0x007700,0x008800,
        0x00AA00,0x00BB00,0x00DD00,0x00EE00,
        // Shades of red w/o 0x33 multiples, starting at index 31
        0x110000,0x220000,0x440000,0x550000,0x770000,0x880000,
        0xAA0000,0xBB0000,0xDD0000,0xEE0000,
        // 0x33 multiples, starting at index 41
        0x000033,0x000066,0x000099,0x0000CC,0x0000FF,
        0x003300,0x003333,0x003366,0x003399,0x0033CC,0x0033FF,
        0x006600,0x006633,0x006666,0x006699,0x0066CC,0x0066FF,
        0x009900,0x009933,0x009966,0x009999,0x0099CC,0x0099FF,
        0x00CC00,0x00CC33,0x00CC66,0x00CC99,0x00CCCC,0x00CCFF,
        0x00FF00,0x00FF33,0x00FF66,0x00FF99,0x00FFCC,0x00FFFF,
        0x330000,0x330033,0x330066,0x330099,0x3300CC,0x3300FF,
        0x333300,0x333333,0x333366,0x333399,0x3333CC,0x3333FF,
        0x336600,0x336633,0x336666,0x336699,0x3366CC,0x3366FF,
        0x339900,0x339933,0x339966,0x339999,0x3399CC,0x3399FF,
        0x33CC00,0x33CC33,0x33CC66,0x33CC99,0x33CCCC,0x33CCFF,
        0x33FF00,0x33FF33,0x33FF66,0x33FF99,0x33FFCC,0x33FFFF,
        0x660000,0x660033,0x660066,0x660099,0x6600CC,0x6600FF,
        0x663300,0x663333,0x663366,0x663399,0x6633CC,0x6633FF,
        0x666600,0x666633,0x666666,0x666699,0x6666CC,0x6666FF,
        0x669900,0x669933,0x669966,0x669999,0x6699CC,0x6699FF,
        0x66CC00,0x66CC33,0x66CC66,0x66CC99,0x66CCCC,0x66CCFF,
        0x66FF00,0x66FF33,0x66FF66,0x66FF99,0x66FFCC,0x66FFFF,
        0x990000,0x990033,0x990066,0x990099,0x9900CC,0x9900FF,
        0x993300,0x993333,0x993366,0x993399,0x9933CC,0x9933FF,
        0x996600,0x996633,0x996666,0x996699,0x9966CC,0x9966FF,
        0x999900,0x999933,0x999966,0x999999,0x9999CC,0x9999FF,
        0x99CC00,0x99CC33,0x99CC66,0x99CC99,0x99CCCC,0x99CCFF,
        0x99FF00,0x99FF33,0x99FF66,0x99FF99,0x99FFCC,0x99FFFF,
        0xCC0000,0xCC0033,0xCC0066,0xCC0099,0xCC00CC,0xCC00FF,
        0xCC3300,0xCC3333,0xCC3366,0xCC3399,0xCC33CC,0xCC33FF,
        0xCC6600,0xCC6633,0xCC6666,0xCC6699,0xCC66CC,0xCC66FF,
        0xCC9900,0xCC9933,0xCC9966,0xCC9999,0xCC99CC,0xCC99FF,
        0xCCCC00,0xCCCC33,0xCCCC66,0xCCCC99,0xCCCCCC,0xCCCCFF,
        0xCCFF00,0xCCFF33,0xCCFF66,0xCCFF99,0xCCFFCC,0xCCFFFF,
        0xFF0000,0xFF0033,0xFF0066,0xFF0099,0xFF00CC,0xFF00FF,
        0xFF3300,0xFF3333,0xFF3366,0xFF3399,0xFF33CC,0xFF33FF,
        0xFF6600,0xFF6633,0xFF6666,0xFF6699,0xFF66CC,0xFF66FF,
        0xFF9900,0xFF9933,0xFF9966,0xFF9999,0xFF99CC,0xFF99FF,
        0xFFCC00,0xFFCC33,0xFFCC66,0xFFCC99,0xFFCCCC,0xFFCCFF,
        0xFFFF00,0xFFFF33,0xFFFF66,0xFFFF99,0xFFFFCC,
        // White, index 255
        0xFFFFFF,
    };

    protected int errorStatus = NO_ERROR;

    public GifOutputStream(final OutputStream out) {
        super(out);
    }

    public int getErrorStatus() {
        return errorStatus;
    }

    public void write(final Image image) throws IOException {
        write(image, ORIGINAL_COLOR, null);
    }

    public void write(final Image image, final int colorMode) throws IOException {
        write(image, colorMode, null);
    }

    public void write(final Image image, final Color transparentColor) throws IOException {
        write(image, ORIGINAL_COLOR, transparentColor);
    }

    public void write(final Image image, final int colorMode, final Color transparentColor) throws IOException {
        errorStatus = NO_ERROR;

        if(image == null)
            return;

        final int width = image.getWidth(null);
        final int height = image.getHeight(null);
        int[] pixels = new int[width * height];
        final PixelGrabber pg = new PixelGrabber(image, 0, 0, width, height, pixels, 0, width);

        try {
            pg.grabPixels();
        } catch(final InterruptedException e) {
            errorStatus = IMAGE_LOAD_FAILED;
            return;
        }

        if((pg.status() & ImageObserver.ABORT) != 0) {
            errorStatus = IMAGE_LOAD_FAILED;
            return;
        }

        int colorCount = 0;
        int[] colorTable = null;
        byte[] bytePixels = null;

        switch(colorMode) {
            case ORIGINAL_COLOR:
                final Map<Integer, Integer> colorSet = getColorSet(pixels);
                colorCount = colorSet.size();
                if(colorCount > 256) {
                    errorStatus = TOO_MANY_COLORS;
                    return;
                }
                colorTable = createColorTable(colorSet, colorCount);
                bytePixels = createBytePixels(pixels, colorSet);
                break;

            case BLACK_AND_WHITE:
                colorCount = 2;
                colorTable = createBWTable();
                bytePixels = createBWBytePixels(pixels);
                break;

            case GRAYSCALE_16:
                colorCount = 16;
                colorTable = create16GrayTable();
                bytePixels = create16GrayBytePixels(pixels);
                break;

            case GRAYSCALE_256:
                colorCount = 256;
                colorTable = create256GrayTable();
                bytePixels = create256GrayBytePixels(pixels);
                break;

            case STANDARD_16_COLORS:
                colorCount = 16;
                colorTable = createStd16ColorTable();
                bytePixels = createStd16ColorBytePixels(pixels);
                break;

            case STANDARD_256_COLORS:
                colorCount = 256;
                colorTable = createStd256ColorTable();
                bytePixels = createStd256ColorBytePixels(pixels);
                break;

            default:
                errorStatus = INVALID_COLOR_MODE;
                return;
        }

        pixels = null;

        int cc1 = colorCount - 1;
        int bitsPerPixel = 0;

        while(cc1 != 0) {
            ++bitsPerPixel;
            cc1 >>= 1;
        }

        writeGIFHeader(width, height, bitsPerPixel);

        writeColorTable(colorTable, bitsPerPixel);

        if(transparentColor != null)
            writeGraphicControlExtension(transparentColor, colorTable);

        writeImageDescriptor(width, height);

        writeCompressedImageData(bytePixels, bitsPerPixel);

        write(0x00); // Terminate picture data.

        write(0x3B); // GIF file terminator.
    }

    protected Map<Integer, Integer> getColorSet(final int[] pixels) {
        final Map<Integer, Integer> colorSet = new HashMap<Integer, Integer>();
        final boolean[] checked = new boolean[pixels.length];
        int needsChecking = pixels.length;
        int color;
        int colorIndex = 0;
        Integer key;

        for(int j = 0; j < pixels.length && needsChecking > 0; ++j) {
            if(!checked[j]) {
                color = pixels[j] & 0x00FFFFFF;
                checked[j] = true;
                --needsChecking;

                key = Integer.valueOf(color);
                colorSet.put(key, Integer.valueOf(colorIndex));
                if(++colorIndex > 256)
                    break;

                for(int j2 = j + 1; j2 < pixels.length; ++j2) {
                    if((pixels[j2] & 0x00FFFFFF) == color) {
                        checked[j2] = true;
                        --needsChecking;
                    }
                }
            }
        }

        return colorSet;
    }

    protected int[] createColorTable(final Map<Integer, Integer> colorSet, final int colorCount) {
        final int[] colorTable = new int[colorCount];

        for(final Integer key: colorSet.keySet())
            colorTable[colorSet.get(key).intValue()] = key.intValue();

        return colorTable;
    }

    protected byte[] createBytePixels(final int[] pixels, final Map<Integer, Integer> colorSet) {
        final byte[] bytePixels = new byte[pixels.length];
        Integer key;
        int colorIndex;

        for(int j = 0; j < pixels.length; ++j) {
            key = Integer.valueOf(pixels[j] & 0x00FFFFFF);
            colorIndex = colorSet.get(key).intValue();
            bytePixels[j] = (byte)colorIndex;
        }

        return bytePixels;
    }

    protected int[] createBWTable() {
        final int[] colorTable = new int[2];

        colorTable[BLACK_INDEX] = 0x000000;
        colorTable[WHITE_INDEX] = 0xFFFFFF;

        return colorTable;
    }

    protected byte[] createBWBytePixels(final int[] pixels) {
        final byte[] bytePixels = new byte[pixels.length];

        for(int j = 0; j < pixels.length; ++j) {
            if(grayscaleValue(pixels[j]) < 0x80)
                bytePixels[j] = (byte)BLACK_INDEX;
            else
                bytePixels[j] = (byte)WHITE_INDEX;
        }

        return bytePixels;
    }

    protected int[] create16GrayTable() {
        final int[] colorTable = new int[16];

        for(int j = 0; j < 16; ++j)
            colorTable[j] = 0x111111 * j;

        return colorTable;
    }

    protected byte[] create16GrayBytePixels(final int[] pixels) {
        final byte[] bytePixels = new byte[pixels.length];

        for(int j = 0; j < pixels.length; ++j) {
            bytePixels[j] = (byte)(grayscaleValue(pixels[j]) / 16);
        }

        return bytePixels;
    }

    protected int[] create256GrayTable() {
        final int[] colorTable = new int[256];

        for(int j = 0; j < 256; ++j)
            colorTable[j] = 0x010101 * j;

        return colorTable;
    }

    protected byte[] create256GrayBytePixels(final int[] pixels) {
        final byte[] bytePixels = new byte[pixels.length];

        for(int j = 0; j < pixels.length; ++j) {
            bytePixels[j] = (byte)grayscaleValue(pixels[j]);
        }

        return bytePixels;
    }

    protected int[] createStd16ColorTable() {
        final int[] colorTable = new int[16];

        for(int j = 0; j < 16; ++j)
            colorTable[j] = standard16[j];

        return colorTable;
    }

    protected byte[] createStd16ColorBytePixels(final int[] pixels) {
        final byte[] bytePixels = new byte[pixels.length];
        int color;
        int minError = 0;
        int error;
        int minIndex;

        for(int j = 0; j < pixels.length; ++j) {
            color = pixels[j] & 0xFFFFFF;
            minIndex = -1;

            for(int k = 0; k < 16; ++k) {
                error = colorMatchError(color, standard16[k]);
                if(error < minError || minIndex < 0) {
                    minError = error;
                    minIndex = k;
                }
            }

            bytePixels[j] = (byte)minIndex;
        }

        return bytePixels;
    }

    protected int[] createStd256ColorTable() {
        final int[] colorTable = new int[256];

        for(int j = 0; j < 256; ++j)
            colorTable[j] = standard256[j];

        return colorTable;
    }

    protected byte[] createStd256ColorBytePixels(final int[] pixels) {
        final byte[] bytePixels = new byte[pixels.length];
        int color;
        int minError = 0;
        int error;
        int minIndex;
        int sampleIndex;

        for(int j = 0; j < pixels.length; ++j) {
            color = pixels[j] & 0xFFFFFF;
            minIndex = -1;

            final int r = (color & 0xFF0000) >> 16;
            final int g = (color & 0x00FF00) >> 8;
            final int b = color & 0x0000FF;

            final int r2 = r / 0x33;
            final int g2 = g / 0x33;
            final int b2 = b / 0x33;

            // Try to match color to a 0x33-multiple color.

            for(int r0 = r2; r0 <= r2 + 1 && r0 < 6; ++r0) {
                for(int g0 = g2; g0 <= g2 + 1 && g0 < 6; ++g0) {
                    for(int b0 = b2; b0 <= b2 + 1 && b0 < 6; ++b0) {
                        sampleIndex = 40 + r0 * 36 + g0 * 6 + b0;
                        if(sampleIndex == 40)
                            sampleIndex = 0;

                        error = colorMatchError(color, standard256[sampleIndex]);
                        if(error < minError || minIndex < 0) {
                            minError = error;
                            minIndex = sampleIndex;
                        }
                    }
                }
            }

            int shadeBase;
            int shadeIndex;

            // Try to match color to a 0x11-multiple pure primary shade.

            if(r > g && r > b) {
                shadeBase = 30;
                shadeIndex = (r + 8) / 0x11;
            } else if(g > r && g > b) {
                shadeBase = 20;
                shadeIndex = (g + 8) / 0x11;
            } else {
                shadeBase = 10;
                shadeIndex = (b + 8) / 0x11;
            }

            if(shadeIndex > 0) {
                shadeIndex -= (shadeIndex / 3);
                sampleIndex = shadeBase + shadeIndex;
                error = colorMatchError(color, standard256[sampleIndex]);
                if(error < minError || minIndex < 0) {
                    minError = error;
                    minIndex = sampleIndex;
                }
            }

            // Try to match color to a 0x11-multiple gray.

            shadeIndex = (grayscaleValue(color) + 8) / 0x11;
            if(shadeIndex > 0) {
                shadeIndex -= (shadeIndex / 3);
                sampleIndex = shadeIndex;
                error = colorMatchError(color, standard256[sampleIndex]);
                if(error < minError || minIndex < 0) {
                    minError = error;
                    minIndex = sampleIndex;
                }
            }

            bytePixels[j] = (byte)minIndex;
        }

        return bytePixels;
    }

    protected int grayscaleValue(final int color) {
        final int r = (color & 0xFF0000) >> 16;
        final int g = (color & 0x00FF00) >> 8;
        final int b = color & 0x0000FF;

        return (r * 30 + g * 59 + b * 11) / 100;
    }

    protected int colorMatchError(final int color1, final int color2) {
        final int r1 = (color1 & 0xFF0000) >> 16;
        final int g1 = (color1 & 0x00FF00) >> 8;
        final int b1 = color1 & 0x0000FF;
        final int r2 = (color2 & 0xFF0000) >> 16;
        final int g2 = (color2 & 0x00FF00) >> 8;
        final int b2 = color2 & 0x0000FF;
        final int dr = (r2 - r1) * 30;
        final int dg = (g2 - g1) * 59;
        final int db = (b2 - b1) * 11;

        return (dr * dr + dg * dg + db * db) / 100;
    }

    protected void writeGIFHeader(final int width, final int height, final int bitsPerPixel) throws IOException {
        write('G');
        write('I');
        write('F');
        write('8');
        write('9');
        write('a');

        writeGIFWord(width);
        writeGIFWord(height);

        int packedBits = 0x80; // Yes, there is a global color table, not ordered.

        packedBits |= ((bitsPerPixel - 1) << 4) | (bitsPerPixel - 1);

        write(packedBits);

        write(0); // Background color index -- not used.

        write(0); // Aspect ratio index -- not specified.
    }

    protected void writeColorTable(final int[] colorTable, final int bitsPerPixel) throws IOException {
        final int colorCount = 1 << bitsPerPixel;

        for(int j = 0; j < colorCount; ++j) {
            if(j < colorTable.length)
                writeGIFColor(colorTable[j]);
            else
                writeGIFColor(0);
        }
    }

    protected void writeGraphicControlExtension(final Color transparentColor,
        final int[] colorTable) throws IOException {
        for(int j = 0; j < colorTable.length; ++j) {
            if(colorTable[j] == (transparentColor.getRGB() & 0xFFFFFF)) {
                write(0x21); // Extension identifier.
                write(0xF9); // Graphic Control Extension identifier.
                write(0x04); // Block size, always 4.
                write(0x01); // Sets transparent color bit. Other bits in this
                             // packed field should be zero.
                write(0x00); // Two bytes of delay time -- not used.
                write(0x00);
                write(j);    // Index of transparent color.
                write(0x00); // Block terminator.
            }
        }
    }

    protected void writeImageDescriptor(final int width, final int height) throws IOException {
        write(0x2C); // Image descriptor identifier;

        writeGIFWord(0); // left postion;
        writeGIFWord(0); // top postion;
        writeGIFWord(width);
        writeGIFWord(height);

        write(0); // No local color table, not interlaced.
    }

    protected void writeGIFWord(final short word) throws IOException {
        writeGIFWord((int)word);
    }

    protected void writeGIFWord(final int word) throws IOException {
        write(word & 0xFF);
        write((word & 0xFF00) >> 8);
    }

    protected void writeGIFColor(final Color color) throws IOException {
        writeGIFColor(color.getRGB());
    }

    protected void writeGIFColor(final int color) throws IOException {
        write((color & 0xFF0000) >> 16);
        write((color & 0xFF00) >> 8);
        write(color & 0xFF);
    }

    /********************************************************************
     * \
     * 
     * | |
     * \
     ********************************************************************/

    protected int rl_pixel;
    protected int rl_basecode;
    protected int rl_count;
    protected int rl_table_pixel;
    protected int rl_table_max;
    protected boolean just_cleared;
    protected int out_bits;
    protected int out_bits_init;
    protected int out_count;
    protected int out_bump;
    protected int out_bump_init;
    protected int out_clear;
    protected int out_clear_init;
    protected int max_ocodes;
    protected int code_clear;
    protected int code_eof;
    protected int obuf;
    protected int obits;
    protected byte[] oblock = new byte[256];
    protected int oblen;

    protected final static int GIFBITS = 12;

    protected void writeCompressedImageData(final byte[] bytePixels, final int bitsPerPixel)
        throws IOException {
        int init_bits = bitsPerPixel;

        if(init_bits < 2)
            init_bits = 2;

        write(init_bits);

        int c;

        obuf = 0;
        obits = 0;
        oblen = 0;
        code_clear = 1 << init_bits;
        code_eof = code_clear + 1;
        rl_basecode = code_eof + 1;
        out_bump_init = (1 << init_bits) - 1;
        /*
         * for images with a lot of runs, making out_clear_init larger will
         * give better compression.
         */
        out_clear_init = (init_bits <= 2) ? 9 : (out_bump_init - 1);
        out_bits_init = init_bits + 1;
        max_ocodes = (1 << GIFBITS) - ((1 << (out_bits_init - 1)) + 3);
        did_clear();
        output(code_clear);
        rl_count = 0;

        for(int j = 0; j < bytePixels.length; ++j) {
            c = bytePixels[j];
            if(c < 0)
                c += 256;

            if((rl_count > 0) && (c != rl_pixel))
                rl_flush();

            if(rl_pixel == c) {
                rl_count++;
            } else {
                rl_pixel = c;
                rl_count = 1;
            }
        }

        if(rl_count > 0)
            rl_flush();

        output(code_eof);
        output_flush();
    }

    protected void write_block() throws IOException {
        write(oblen);
        write(oblock, 0, oblen);
        oblen = 0;
    }

    protected void block_out(final int c) throws IOException {
        oblock[oblen++] = (byte)c;
        if(oblen >= 255)
            write_block();
    }

    protected void block_flush() throws IOException {
        if(oblen > 0)
            write_block();
    }

    protected void output(final int val) throws IOException {
        obuf |= val << obits;
        obits += out_bits;
        while(obits >= 8) {
            block_out(obuf & 0xFF);
            obuf >>= 8;
            obits -= 8;
        }
    }

    protected void output_flush() throws IOException {
        if(obits > 0)
            block_out(obuf);
        block_flush();
    }

    protected void did_clear() {
        out_bits = out_bits_init;
        out_bump = out_bump_init;
        out_clear = out_clear_init;
        out_count = 0;
        rl_table_max = 0;
        just_cleared = true;
    }

    protected void output_plain(final int c) throws IOException {
        just_cleared = false;
        output(c);
        out_count++;
        if(out_count >= out_bump) {
            out_bits++;
            out_bump += 1 << (out_bits - 1);
        }
        if(out_count >= out_clear) {
            output(code_clear);
            did_clear();
        }
    }

    protected int isqrt(final int x) {
        int r;
        int v;

        if(x < 2)
            return x;

        for(v = x, r = 1; v != 0; v >>= 2, r <<= 1);

        while(true) {
            v = ((x / r) + r) / 2;
            if((v == r) || (v == r + 1))
                return r;
            r = v;
        }
    }

    protected int compute_triangle_count(int count, final int nrepcodes) {
        int perrep;
        int cost;

        cost = 0;
        perrep = (nrepcodes * (nrepcodes + 1)) / 2;
        while(count >= perrep) {
            cost += nrepcodes;
            count -= perrep;
        }
        if(count > 0) {
            int n = isqrt(count);
            while((n * (n + 1)) >= 2 * count)
                n--;
            while((n * (n + 1)) < 2 * count)
                n++;
            cost += n;
        }

        return cost;
    }

    protected void max_out_clear() {
        out_clear = max_ocodes;
    }

    protected void reset_out_clear() throws IOException {
        out_clear = out_clear_init;
        if(out_count >= out_clear) {
            output(code_clear);
            did_clear();
        }
    }

    protected void rl_flush_fromclear(int count) throws IOException {
        int n;

        max_out_clear();
        rl_table_pixel = rl_pixel;
        n = 1;
        while(count > 0) {
            if(n == 1) {
                rl_table_max = 1;
                output_plain(rl_pixel);
                count--;
            } else if(count >= n) {
                rl_table_max = n;
                output_plain(rl_basecode + n - 2);
                count -= n;
            } else if(count == 1) {
                rl_table_max++;
                output_plain(rl_pixel);
                count = 0;
            } else {
                rl_table_max++;
                output_plain(rl_basecode + count - 2);
                count = 0;
            }

            if(out_count == 0)
                n = 1;
            else
                n++;
        }

        reset_out_clear();
    }

    protected void rl_flush_clearorrep(int count) throws IOException {
        int withclr;

        withclr = 1 + compute_triangle_count(count, max_ocodes);
        if(withclr < count) {
            output(code_clear);
            did_clear();
            rl_flush_fromclear(count);
        } else {
            for(; count > 0; count--)
                output_plain(rl_pixel);
        }
    }

    protected void rl_flush_withtable(final int count) throws IOException {
        int repmax;
        int repleft;
        int leftover;

        repmax = count / rl_table_max;
        leftover = count % rl_table_max;
        repleft = (leftover != 0 ? 1 : 0);
        if(out_count + repmax + repleft > max_ocodes) {
            repmax = max_ocodes - out_count;
            leftover = count - (repmax * rl_table_max);
            repleft = 1 + compute_triangle_count(leftover, max_ocodes);
        }

        if(1 + compute_triangle_count(count, max_ocodes) < repmax + repleft) {
            output(code_clear);
            did_clear();
            rl_flush_fromclear(count);
            return;
        }

        max_out_clear();
        for(; repmax > 0; repmax--)
            output_plain(rl_basecode + rl_table_max - 2);
        if(leftover != 0) {
            if(just_cleared) {
                rl_flush_fromclear(leftover);
            } else if(leftover == 1) {
                output_plain(rl_pixel);
            } else {
                output_plain(rl_basecode + leftover - 2);
            }
        }
        reset_out_clear();
    }

    protected void rl_flush() throws IOException {
        // int table_reps;
        // int table_extra;

        if(rl_count == 1) {
            output_plain(rl_pixel);
            rl_count = 0;
            return;
        }
        if(just_cleared) {
            rl_flush_fromclear(rl_count);
        } else if((rl_table_max < 2) || (rl_table_pixel != rl_pixel)) {
            rl_flush_clearorrep(rl_count);
        } else {
            rl_flush_withtable(rl_count);
        }

        rl_count = 0;
    }

    /******** END OF IMPORTED GIF COMPRESSION CODE ********/
}
