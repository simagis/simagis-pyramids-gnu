package com.simagis.pyramid.openslide;

import net.algart.arrays.*;
import net.algart.simagis.pyramid.AbstractPlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidTools;

import java.awt.*;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class OpenSlidePlanePyramidSource extends AbstractPlanePyramidSource implements PlanePyramidSource {
    private static final boolean DISABLE_BACKGROUND_FOR_LITTLE_HOLES = Arrays.SystemSettings.getBooleanProperty(
        "com.simagis.pyramid.openslide.disableBackgroundForLittleHoles", false);
    // not enough tool for blocking Moire phenomenon - not used now

    private static final int DEBUG_LEVEL = 1;
    private static final double INV_255 = 1.0 / 255.0;

    private final File svsFile;
    private final int compression;
    private final int numberOfResolutions;
    private final boolean useAlphaForBackground;
    private final int backgroundRed, backgroundGreen, backgroundBlue; //0..255
    private final int maxAlphaForEnforcingBackground; // if alpha < this value, we always use our background
    private final long totalDimX;
    private final long totalDimY;
    private final int bandCount;
    private final List<long[]> dimensions;
    private final LargeDataHolder largeData = new LargeDataHolder();

    public OpenSlidePlanePyramidSource(File svsFile) throws IOException {
        this(null, svsFile, 0);
    }

    public OpenSlidePlanePyramidSource(ArrayContext context, File svsFile) throws IOException {
        this(context, svsFile, 0);
    }

    public OpenSlidePlanePyramidSource(ArrayContext context, File svsFile, int compression) throws IOException {
        this(context, svsFile, compression, null, 4096, 0.0);
    }

    // compression=0 means automatic detection
    public OpenSlidePlanePyramidSource(
        ArrayContext context, File svsFile, int compression,
        Color backgroundColor,
        int minPyramidLevelSide,
        // - sometimes OpenSlide library tries to build extra small levels and does it incorrectly (moire phenomenon)
        double maxAlphaForEnforcingBackground)
        throws IOException
    {
        super(context);
        if (svsFile == null)
            throw new NullPointerException("Null svsFile");
        this.svsFile = svsFile;
        this.largeData.init();
        boolean success = false;
        try {
            this.totalDimX = largeData.openSlide.getLevel0Width();
            this.totalDimY = largeData.openSlide.getLevel0Height();
            this.bandCount = 3; // maybe, in future here will be better code
            if (DEBUG_LEVEL >= 1) {
                System.out.printf("OpenSlide opens image %s: %dx%d, %d bands%n",
                    svsFile, totalDimX, totalDimY, bandCount);
            }
            long lastDimX = this.totalDimX;
            long lastDimY = this.totalDimY;
            int maxUsedNumberOfResolutions = Math.max(1, largeData.openSlide.getLevelCount());
            this.dimensions = new ArrayList<long[]>();
            this.dimensions.add(new long[] {bandCount, this.totalDimX, this.totalDimY});
            for (int k = 1; k < maxUsedNumberOfResolutions; k++) {
                final long newDimX = largeData.openSlide.getLevelWidth(k);
                final long newDimY = largeData.openSlide.getLevelHeight(k);
                if (DEBUG_LEVEL >= 1) {
                    System.out.printf("OpenSlide checks layer #%d: %dx%d%n", k, newDimX, newDimY);
                }
                if (k == 1 && compression == 0) {
                    compression = PlanePyramidTools.findCompression(
                        new long[] {bandCount, lastDimX, lastDimY},
                        new long[] {bandCount, newDimX, newDimY});
                    if (compression == 0)
                        throw new IllegalArgumentException("Cannot find suitable compression for first "
                            + "two levels " + lastDimX + "x" + lastDimY + " and " + newDimX + "x" + newDimY);
                    if (DEBUG_LEVEL >= 1) {
                        System.out.println("OpenSlide automatically detected compression " + compression);
                    }
                }
                if (newDimX < minPyramidLevelSide && newDimY < minPyramidLevelSide) {
                    break;
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
            this.useAlphaForBackground = backgroundColor != null;
            if (this.useAlphaForBackground) {
                this.backgroundRed = backgroundColor.getRed();
                this.backgroundGreen = backgroundColor.getGreen();
                this.backgroundBlue = backgroundColor.getBlue();
            } else {
                this.backgroundRed = -1;
                this.backgroundGreen = -1;
                this.backgroundBlue = -1;
            }
            this.maxAlphaForEnforcingBackground = (int) Math.round(maxAlphaForEnforcingBackground * 255.0);
            if (DEBUG_LEVEL >= 1) {
                System.out.println("OpenSlide found " + this.numberOfResolutions + " layers with correct compression"
                    + " among " + largeData.openSlide.getLevelCount() + " total layers");
                System.out.println("OpenSlide instantiating " + this);
            }
            success = true;
        } finally {
            if (!success) {
                largeData.freeResources();
            }
        }
    }


    public int numberOfResolutions() {
        return numberOfResolutions;
    }

    public int bandCount() {
        return bandCount;
    }

    public long[] dimensions(int resolutionLevel) {
        return dimensions.get(resolutionLevel).clone();
    }

    public void loadResources() {
        try {
            largeData.init();
        } catch (IOException e) {
            throw new IOError(e);
        }
        super.loadResources();
    }

    protected void freeResources() {
        super.freeResources();
        largeData.freeResources();
    }

    @Override
    protected Matrix<? extends PArray> readLittleSubMatrix(
        int resolutionLevel, long fromX, long fromY, long toX, long toY)
    {
        checkSubMatrixRanges(resolutionLevel, fromX, fromY, toX, toY, true);
        final int sizeX = (int) (toX - fromX);
        final int sizeY = (int) (toY - fromY);
        largeData.lock.lock();
        try {
            largeData.init();
            int[] packedData = new int[sizeX * sizeY];
            if (DEBUG_LEVEL >= 2) {
                System.out.printf("OpenSlide reading R%d: %d..%d x %d..%d%n",
                    resolutionLevel, fromX, toX, fromY, toY);
            }
            for (int level = 0; level < resolutionLevel; level++) {
                // paintRegionARGB needs coordinates in terms of the zero level
                fromX *= compression;
                fromY *= compression;
            }
            if (sizeX > 0 || sizeY > 0) { // to be on the safe side: not try to read zero-size frame
                largeData.openSlide.paintRegionARGB(
                    packedData, fromX, fromY, getOpenSlideLevel(resolutionLevel), sizeX, sizeY);
            }
//            for (int k = 0; k < Math.min(sizeY, 20); k++) {
//                System.out.printf("%d: %s%n", k,
//                    JArrays.toString(JArrays.copyOfRange(packedData, k * sizeX, (k + 1) * sizeX),
//                        java.util.Locale.US, "%x", ",", 5000));
//            }
            byte[] data = new byte[bandCount * sizeX * sizeY];
            switch (bandCount) {
                case 0:
                    break; // to be on the safe side
                case 1:
                    for (int i = 0, disp = 0; i < packedData.length; i++, disp++) {
                        data[disp] = (byte) packedData[i];
                    }
                    break;
                case 2:
                case 3:
                    if (!useAlphaForBackground) {
                        for (int i = 0, disp = 0; i < packedData.length; i++, disp += bandCount) {
                            int v = packedData[i];
                            data[disp] = (byte) (v >>> 16);
                            data[disp + 1] = (byte) (v >>> 8);
                            data[disp + 2] = (byte) v;
                        }
                    } else {
                        for (int y = 0, disp = 0, i = 0; y < sizeY; y++) {
                            for (int x = 0; x < sizeX; x++, i++, disp += bandCount) {
                                int v = packedData[i];
                                int alpha = v >>> 24;
                                if (alpha == 0) {
                                    data[disp] = (byte) backgroundRed;
                                    data[disp + 1] = (byte) backgroundGreen;
                                    data[disp + 2] = (byte) backgroundBlue;
                                    continue;
                                }
                                if (DISABLE_BACKGROUND_FOR_LITTLE_HOLES) {
                                    boolean leftFF = x == 0 || (packedData[i - 1] >>> 24) == 0xFF;
                                    boolean rightFF = x == sizeX - 1 || (packedData[i + 1] >>> 24) == 0xFF;
                                    boolean upFF = y == 0 || (packedData[i - sizeX] >>> 24) == 0xFF;
                                    boolean downFF = y == sizeY - 1 || (packedData[i + sizeX] >>> 24) == 0xFF;
                                    boolean littleHole = (leftFF && rightFF) || (upFF && downFF);
                                    if (littleHole) {
                                        alpha = 0xFF;
                                    }
                                }
                                int r = (v >>> 16) & 0xFF;
                                int g = (v >>> 8) & 0xFF;
                                int b = v & 0xFF;
                                if (alpha == 0xFF) {
                                    data[disp] = (byte) r;
                                    data[disp + 1] = (byte) g;
                                    data[disp + 2] = (byte) b;
                                    continue;
                                }
                                boolean enforceBackground = alpha < maxAlphaForEnforcingBackground;
                                if (enforceBackground) {
                                    data[disp] = (byte) backgroundRed;
                                    data[disp + 1] = (byte) backgroundGreen;
                                    data[disp + 2] = (byte) backgroundBlue;
                                    continue;
                                }
                                data[disp] = (byte) (INV_255 * (r * alpha + backgroundRed * (255 - alpha)));
                                data[disp + 1] = (byte) (INV_255 * (g * alpha + backgroundGreen * (255 - alpha)));
                                data[disp + 2] = (byte) (INV_255 * (b * alpha + backgroundBlue * (255 - alpha)));
                            }
                        }
                    }
                    break;
                default:
                    for (int i = 0, disp = 0; i < packedData.length; i++, disp += bandCount) {
                        int v = packedData[i];
                        data[disp] = (byte) (v >>> 16);
                        data[disp + 1] = (byte) (v >>> 8);
                        data[disp + 2] = (byte) v;
                        data[disp + 3] = (byte) (v >>> 24);
                    }
                    break;
            }
            return Matrices.matrix(SimpleMemoryModel.asUpdatableByteArray(data), bandCount, sizeX, sizeY);
        } catch (IOException e) {
            throw new IOError(e);
        } finally {
            largeData.lock.unlock();
        }
    }

    private static int getOpenSlideLevel(int resolutionLevel) {
        return resolutionLevel; // no reasons to skip levels here
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
        private volatile OpenSlideWrapper openSlide = null;
        private final Lock lock = new ReentrantLock();

        private void init() throws IOException {
            lock.lock();
            try {
                if (openSlide == null) {
                    if (DEBUG_LEVEL >= 1) {
                        System.out.println("OpenSlide reinitializing " + this);
                    }
                    openSlide = new OpenSlideWrapper(svsFile);
                }
            } finally {
                lock.unlock();
            }
        }

        private void freeResources() {
            lock.lock();
            try {
                if (openSlide != null) {
                    if (DEBUG_LEVEL >= 1) {
                        System.out.println("OpenSlide disposing " + this);
                    }
                    openSlide.dispose();
                    openSlide = null;
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (DEBUG_LEVEL >= 1) {
                    System.out.println("OpenSlide finalizing " + this);
                }
                OpenSlidePlanePyramidSource.this.freeResources(FlushMethod.STANDARD);
            } finally {
                super.finalize();
            }
        }
    }
}
