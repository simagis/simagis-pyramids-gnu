/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Daniel Alievsky, AlgART Laboratory (http://algart.net)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.simagis.pyramid.loci;

import com.simagis.pyramid.PlanePyramidTools;
import com.simagis.pyramid.PlanePyramidSource;
import loci.common.DataTools;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import net.algart.arrays.*;
import net.algart.math.Range;
import net.algart.math.functions.LinearFunc;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.channels.NotYetConnectedException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LociPlanePyramidSource extends AbstractArrayProcessorWithContextSwitching
    implements PlanePyramidSource, ArrayProcessorWithContextSwitching
{
    private static final Logger LOGGER = Logger.getLogger(LociPlanePyramidSource.class.getName());

    private static final int DEFAULT_LOCI_COMPRESSION = 2;

    private final File imageFile;
    private final String imageFormat;
    private final int selectedSeries;
    private final Boolean flattenedResolutions;
    private final int compression;
    private final int numberOfResolutions;
    private final int bandCount;
    private final List<long[]> dimensions;
    private final LargeDataHolder largeData = new LargeDataHolder();

    private volatile boolean normalizeFractionalNumberOfBytesPerPixel = true;
    private volatile boolean autoContrastIfMoreThan8Bits = false;
    private volatile boolean autoContrastAlways = false;
    private volatile int imagePlaneIndex = 0;

    public LociPlanePyramidSource(File imageFile) throws IOException, FormatException {
        this(null, imageFile);
    }

    public LociPlanePyramidSource(ArrayContext context, File imageFile)
        throws IOException, FormatException
    {
        this(context, imageFile, null, null, null);
    }

    // compression=0 means automatic detection
    // flattenedResolutions=false provides more automatic mode
    public LociPlanePyramidSource(
        ArrayContext context, File imageFile,
        String imageFormat, Integer requiredSeries, Boolean flattenedResolutions)
        throws IOException, FormatException
    {
        super(context);
        if (imageFile == null) {
            throw new NullPointerException("Null svsFile");
        }
        if (requiredSeries != null && requiredSeries < 0) {
            throw new IllegalArgumentException("Negative requires series");
        }
        this.imageFile = imageFile;
        this.imageFormat = imageFormat;
        this.flattenedResolutions = flattenedResolutions;
        this.largeData.init();
        // warning: selectedSeries is not correct yet and should be reset in the reader later
        boolean success = false;
        try {
            final IFormatReader r = tryToCreateReader();
            final int seriesCount = this.largeData.reader.getSeriesCount();
            if (seriesCount <= 0) {
                throw new FormatException("Zero or negative number of series " + seriesCount + " in " + imageFile);
            }
            int selectedSeries = 0;
            if (requiredSeries != null) {
                selectedSeries = Math.min(seriesCount - 1, requiredSeries);
            } else {
                int maxResolutionCount = Integer.MIN_VALUE;
                for (int series = 0; series < seriesCount; series++) {
                    this.largeData.reader.setSeries(series);
                    int count = this.largeData.reader.getResolutionCount();
                    assert count >= 0 :
                        "Negative " + this.largeData.reader.getClass() + ".getResolutionCount() for series " + series;
                    if (this.largeData.reader.isThumbnailSeries()) { // note: we never avoid previous assert
                        continue;
                    }
                    if (count > maxResolutionCount) {
                        selectedSeries = series;
                        maxResolutionCount = count;
                    }
                }
            }
            this.largeData.reader.setSeries(selectedSeries); // finishing initializing largeData
            this.selectedSeries = selectedSeries;
            this.bandCount = this.largeData.reader.getRGBChannelCount();
            final long dimX = this.largeData.reader.getSizeX();
            final long dimY = this.largeData.reader.getSizeY();
            if (DEBUG_LEVEL >= 1) {
                System.out.printf("Loci reader %s opens image %s: %dx%d, %d bands, %d series, "
                        + "current (" + (requiredSeries != null ? "required" : "maximal")
                        + ") series %d, flattening resolution: %s%n",
                    r == null ? "(universal)" : this.largeData.reader.getClass().getName(),
                    imageFile,
                    dimX, dimY, this.bandCount,
                    seriesCount, selectedSeries,
                    this.largeData.reader.hasFlattenedResolutions());
                printReaderInfo(this.largeData.reader);
            }
            long lastDimX = dimX;
            long lastDimY = dimY;
            int maxUsedNumberOfLevels = Math.max(1, this.largeData.reader.getResolutionCount());
            this.dimensions = new ArrayList<long[]>();
            this.dimensions.add(new long[] {bandCount, dimX, dimY});
            int compression = DEFAULT_LOCI_COMPRESSION; // for a case maxUsedNumberOfLevels=1
            for (int k = 1; k < maxUsedNumberOfLevels; k++) {
                setResolutionLevel(k);
                long newDimX = this.largeData.reader.getSizeX();
                long newDimY = this.largeData.reader.getSizeY();
                if (k == 1) {
                    compression = PlanePyramidTools.findCompression(
                        new long[] {bandCount, lastDimX, lastDimY},
                        new long[] {bandCount, newDimX, newDimY});
                    if (compression == 0) {
                        throw new IllegalArgumentException("Cannot find suitable compression for first "
                            + "two levels " + lastDimX + "x" + lastDimY + " and " + newDimX + "x" + newDimY);
                    }
                    if (DEBUG_LEVEL >= 1) {
                        System.out.println("Loci reader automatically detected compression " + compression);
                    }
                }
                if (!PlanePyramidTools.isDimensionsRelationCorrect(
                    new long[] {bandCount, lastDimX, lastDimY},
                    new long[] {bandCount, newDimX, newDimY},
                    compression))
                {
                    break;
                }
                lastDimX = newDimX;
                lastDimY = newDimY;
                this.dimensions.add(new long[] {bandCount, newDimX, newDimY});
            }
            this.compression = compression;
            this.numberOfResolutions = this.dimensions.size();
            if (DEBUG_LEVEL >= 1) {
                System.out.println("Loci reader found " + this.numberOfResolutions + " layers with correct compression"
                    + " among " + seriesCount + " total layers");
                System.out.println("Loci reader instantiating " + this);
            }
            success = true;
        } finally {
            if (!success) {
                largeData.freeResources();
            }
        }
    }

    public static boolean isLociFile(File imageFile) {
        final ImageReader imageReader = new ImageReader();
        return imageReader.isThisType(imageFile.getAbsolutePath(), true);
    }

    public IFormatReader getReader() {
        largeData.lock.lock();
        try {
            largeData.init();
            return largeData.reader;
        } catch (IOException e) {
            throw new IOError(e);
        } catch (FormatException e) {
            throw new IOError(e);
        } finally {
            largeData.lock.unlock();
        }
    }

    public int getSelectedSeries() {
        return selectedSeries;
    }

    public boolean isNormalizeFractionalNumberOfBytesPerPixel() {
        return normalizeFractionalNumberOfBytesPerPixel;
    }

    public void setNormalizeFractionalNumberOfBytesPerPixel(boolean normalizeFractionalNumberOfBytesPerPixel) {
        this.normalizeFractionalNumberOfBytesPerPixel = normalizeFractionalNumberOfBytesPerPixel;
    }

    public boolean isAutoContrastIfMoreThan8Bits() {
        return autoContrastIfMoreThan8Bits;
    }

    public void setAutoContrastIfMoreThan8Bits(boolean autoContrastIfMoreThan8Bits) {
        this.autoContrastIfMoreThan8Bits = autoContrastIfMoreThan8Bits;
    }

    public boolean isAutoContrastAlways() {
        return autoContrastAlways;
    }

    public void setAutoContrastAlways(boolean autoContrastAlways) {
        this.autoContrastAlways = autoContrastAlways;
    }

    public int getImagePlaneIndex() {
        return imagePlaneIndex;
    }

    public void setImagePlaneIndex(int imagePlaneIndex) {
        if (imagePlaneIndex < 0) {
            throw new IllegalArgumentException("Negative image plane index");
        }
        this.imagePlaneIndex = imagePlaneIndex;
    }

    public int numberOfResolutions() {
        return numberOfResolutions;
    }

    public int compression() {
        return compression;
    }

    public int bandCount() {
        return bandCount;
    }

    public boolean isResolutionLevelAvailable(int resolutionLevel) {
        return true;
    }

    public boolean[] getResolutionLevelsAvailability() {
        boolean[] result = new boolean[numberOfResolutions()];
        JArrays.fillBooleanArray(result, true);
        return result;
    }


    public long[] dimensions(int resolutionLevel) {
        return dimensions.get(resolutionLevel).clone();
    }

    public boolean isElementTypeSupported() {
        return false;
    }

    public Class<?> elementType() throws UnsupportedOperationException {
        throw new UnsupportedOperationException("elementType() method is not supported by "
            + super.getClass()); // avoiding IDEA bug
    }

    public boolean isDataReady() {
        return true;
    }

    public void loadResources() {
        try {
            largeData.init();
        } catch (IOException e) {
            throw new IOError(e);
        } catch (FormatException e) {
            throw new IOError(e);
        }
    }

    public void freeResources(FlushMethod flushMethod) {
        largeData.freeResources();
    }

    public boolean isFullMatrixSupported() {
        return context() != null;
    }

    public Matrix<? extends PArray> readFullMatrix(int resolutionLevel)
        throws NoSuchElementException, NotYetConnectedException, UnsupportedOperationException
    {
        if (context() == null) {
            throw new UnsupportedOperationException("readFullMatrix method must not be used " +
                "when the context is not specified");
        }
        final long[] dimensions = dimensions(resolutionLevel);
        return readSubMatrix(resolutionLevel, 0, 0, dimensions[1], dimensions[2]);
    }

    public boolean isSpecialMatrixSupported(SpecialImageKind kind) {
        return false;
    }

    public Matrix<? extends PArray> readSpecialMatrix(SpecialImageKind kind) throws NotYetConnectedException {
        if (kind == null) {
            throw new NullPointerException("Null image kind");
        }
        int resolutionLevel = numberOfResolutions() - 1;
        final long[] dimensions = dimensions(resolutionLevel);
        return readSubMatrix(resolutionLevel, 0, 0, dimensions[DIM_WIDTH], dimensions[DIM_HEIGHT]);
    }

    @Override
    public Matrix<? extends PArray> readSubMatrix(
        int resolutionLevel, long fromX, long fromY, long toX, long toY)
    {
        final long[] dim = dimensions(resolutionLevel);
        final long dimX = dim[DIM_WIDTH];
        final long dimY = dim[DIM_HEIGHT];

        if (fromX < 0 || fromY < 0 || fromX > toX || fromY > toY || toX > dimX || toY > dimY) {
            throw new IndexOutOfBoundsException("Illegal fromX/fromY/toX/toY: must be in ranges 0.."
                + dimX + ", 0.." + dimY + ", fromX<=toX, fromY<=toY");
        }
        if (toX - fromX > Integer.MAX_VALUE || toY - fromY > Integer.MAX_VALUE ||
            (toX - fromX) * (toY - fromY) >= Integer.MAX_VALUE / bandCount)
        {
            throw new IllegalArgumentException("Too large rectangle " + (toX - fromX) + "x" + (toY - fromY));
        }
        final int sizeX = (int) (toX - fromX);
        final int sizeY = (int) (toY - fromY);
        largeData.lock.lock();
        try {
            largeData.init();
            if (DEBUG_LEVEL >= 2) {
                System.out.printf("Loci reading R%d: %d..%d x %d..%d%n",
                    resolutionLevel, fromX, toX, fromY, toY);
            }
            setResolutionLevel(resolutionLevel);
            final int pixelType = this.largeData.reader.getPixelType();
            final int bytesPerPixel = FormatTools.getBytesPerPixel(pixelType);
            final int bitsPerPixel = this.largeData.reader.getBitsPerPixel();
            byte[] bytes =
                sizeX == 0 || sizeY == 0 ?
                    new byte[0] : // avoiding possible exceptions while reading zero-size frame by Loci
                    this.largeData.reader.openBytes(
                        Math.min(imagePlaneIndex, Math.max(this.largeData.reader.getImageCount() - 1, 0)),
                        (int) fromX, (int) fromY, sizeX, sizeY);
            if (bytes.length != sizeX * sizeY * bandCount * bytesPerPixel) {
                throw new AssertionError("Invalid usage of Loci API: "
                    + "incorrect number of bytes in the returned array ("
                    + bytes.length + " instead of " + sizeX * sizeY * bandCount * bytesPerPixel
                    + "=" + sizeX + "*" + sizeY + "*" + bandCount + "*" + bytesPerPixel + ")");
            }
            if (!this.largeData.reader.isInterleaved() && bandCount > 1) {
                final int bandSize = sizeX * sizeY * bytesPerPixel;
                byte[] interleavedBytes = new byte[bandCount * bandSize];
                if (bytesPerPixel == 1) { // optimization
                    for (int i = 0, disp = 0; i < bandSize; i += bytesPerPixel) {
                        for (int j = 0, bandDisp = 0; j < bandCount; j++, bandDisp += bandSize) {
                            interleavedBytes[disp++] = bytes[i + bandDisp];
                        }
                    }
                } else {
                    for (int i = 0, disp = 0; i < bandSize; i += bytesPerPixel) {
                        for (int j = 0, bandDisp = 0; j < bandCount; j++, bandDisp += bandSize) {
                            for (int k = 0; k < bytesPerPixel; k++) {
                                interleavedBytes[disp++] = bytes[i + bandDisp + k];
                            }
                        }
                    }
                }
                bytes = interleavedBytes;
            }
            Object data = DataTools.makeDataArray(bytes,
                bytesPerPixel,
                FormatTools.isFloatingPoint(pixelType),
                this.largeData.reader.isLittleEndian());
//            for (int k = 0; k < Math.min(sizeY, 20); k++) {
//                System.out.printf("%d: %s%n", k,
//                    JArrays.toString(JArrays.copyOfRange(data, k * sizeX * bandCount, (k + 1) * sizeX * bandCount),
//                        java.util.Locale.US, "%x", ",", 5000));
//            }
            PArray array = (PArray) SimpleMemoryModel.asUpdatableArray(data);
            boolean autoContrast = autoContrastAlways || (autoContrastIfMoreThan8Bits && array.bitsPerElement() > 8);
            if (autoContrast) {
                Range srcRange = Arrays.rangeOf(array);
                if (srcRange.size() > 0.0) {
                    Range destRange = Range.valueOf(0.0, array.maxPossibleValue(1.0));
                    array = Arrays.asFuncArray(
                        LinearFunc.getInstance(destRange, srcRange),
                        array.type(), array);
                }
            } else if (normalizeFractionalNumberOfBytesPerPixel
                && array instanceof PIntegerArray
                && array.bitsPerElement() > bitsPerPixel)
            {
                long srcMax = (1L << bitsPerPixel) - 1;
                long destMax = ((PIntegerArray) array).maxPossibleValue();
                array = Arrays.asFuncArray(
                    LinearFunc.getInstance(0.0, (double) destMax / (double) srcMax),
                    array.type(), array);
            }
            return Matrices.matrix(array, bandCount, sizeX, sizeY);
        } catch (IOException e) {
            throw new IOError(e);
        } catch (FormatException e) {
            throw new IOError(PlanePyramidTools.rmiSafeWrapper(e));
        } finally {
            largeData.lock.unlock();
        }
    }

    private IFormatReader createReader() {
        IFormatReader reader = tryToCreateReader();
        return reader != null ? reader : new ImageReader();
    }

    private IFormatReader tryToCreateReader() {
        if (imageFormat != null) {
            final String className = "loci.formats.in." + imageFormat + "Reader";
            final String msg = className + "; trying to use default ImageReader";
            try {
                return (IFormatReader) Class.forName(className).newInstance();
            } catch (ClassNotFoundException exc) {
                LOGGER.warning("Unknown Loci reader for format " + imageFormat + ": " + msg);
            } catch (InstantiationException exc) {
                LOGGER.warning("Cannot instantiate Loci reader " + msg);
            } catch (IllegalAccessException exc) {
                LOGGER.warning("Cannot access Loci reader " + msg);
            }
        }
        return null;
    }

    private void setResolutionLevel(int resolutionLevel) {
        largeData.reader.setResolution(resolutionLevel); // better way, requiring flattenedResolutions=false
    }

    private static void printReaderInfo(IFormatReader reader) {
        int originalSeries = reader.getSeries();
        for (int k = 0, n = reader.getSeriesCount(); k < n; k++) {
            reader.setSeries(k);
            System.out.printf("Loci reader checks series %d: image %dx%dx%dx%dx%d (XYZCT), %d image planes, "
                    + "%s resolutions, %d bits/pixel, %d RGB channels, channel dim lengths: {%s}, "
                    + "%s resolutions (current resolution %s);%n"
                    + "pixel type %d (%d bytes/pixel, %s %s), %s, "
                    + "isRGB: %s, %s, "
                    + "indexed: %s, interleaved: %s%n",
                k,
                reader.getSizeX(), reader.getSizeY(), reader.getSizeZ(),
                reader.getSizeC(), reader.getSizeT(),
                reader.getImageCount(),
                reader.getResolutionCount(),
                reader.getBitsPerPixel(),
                reader.getRGBChannelCount(),
                JArrays.toString(reader.getChannelDimLengths(), ",", 100),
                reader.getResolutionCount(),
                reader.getResolution(),
                reader.getPixelType(),
                FormatTools.getBytesPerPixel(reader.getPixelType()),
                FormatTools.isSigned(reader.getPixelType()) ? "signed" : "unsigned",
                FormatTools.isFloatingPoint(reader.getPixelType()) ? "floating-point" : "integer",
                reader.isThumbnailSeries() ? "THUMBNAILS" : "normal series",
                reader.isRGB(),
                reader.isFalseColor() ? "false colors" : "true colors",
                reader.isIndexed(), reader.isInterleaved());
            int resolutionCount = reader.getResolutionCount();
            int originalResolution = reader.getResolution();
            for (int i = 0; i < resolutionCount; i++) {
                reader.setResolution(i);
                System.out.printf(
                    "  Loci reader checks resolution %d: image %dx%dx%dx%dx%d (XYZCT), %d image planes, "
                        + "%d bits/pixel, %d RGB channels, channel dim lengths: {%s}, "
                        + "pixel type %d, series %s, interleaved: %s%n",
                    i,
                    reader.getSizeX(), reader.getSizeY(), reader.getSizeZ(),
                    reader.getSizeC(), reader.getSizeT(),
                    reader.getImageCount(),
                    reader.getBitsPerPixel(),
                    reader.getRGBChannelCount(),
                    JArrays.toString(reader.getChannelDimLengths(), ",", 100),
                    reader.getPixelType(),
                    reader.getSeries(),
                    reader.isInterleaved());
            }
            reader.setResolution(originalResolution);
        }
        reader.setSeries(originalSeries);
    }

    // Important! PlanePyramidSource objects are often cloned, usually before every reading data,
    // because this class extends AbstractArrayProcessorWithContextSwitching. It can lead to serious problems.
    // 1) If we shall implement finalize() method in that class, it will be often called in clones also,
    // and it will lead to a bug: files will be closed, though other clones are active yet.
    // 2) If someone will call freeResources() in that class, then all clones will be created,
    // since that time, in this (disposed) state; so all clones will work normally, but very slowly:
    // the files will be reopened every time when PlanePyramid needs to read data and creates a clone for this.
    // LargeDataHolder class resolves all these problems, because the reference to it is shared among all clones.
    private class LargeDataHolder {
        private volatile IFormatReader reader = null;
        private final Lock lock = new ReentrantLock();

        private void init() throws IOException, FormatException {
            lock.lock();
            try {
                if (reader == null) {
                    if (DEBUG_LEVEL >= 1) {
                        System.out.println("Loci reader initializing " + this);
                    }
                    this.reader = createReader();
                    if (flattenedResolutions != null) {
                        this.reader.setFlattenedResolutions(flattenedResolutions);
                    }
                    this.reader.setId(imageFile.getAbsolutePath());
                    this.reader.setSeries(selectedSeries);
                }
            } finally {
                lock.unlock();
            }
        }


        private void freeResources() {
            lock.lock();
            try {
                if (reader != null) {
                    if (DEBUG_LEVEL >= 1) {
                        System.out.println("Loci reader disposing " + this);
                    }
                    try {
                        reader.close(false);
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "Cannot close Loci reader", e);
                    }
                    reader = null;
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (DEBUG_LEVEL >= 1) {
                    System.out.println("Loci reader finalizing " + this);
                }
                LociPlanePyramidSource.this.freeResources(FlushMethod.STANDARD);
            } finally {
                super.finalize();
            }
        }
    }
}
