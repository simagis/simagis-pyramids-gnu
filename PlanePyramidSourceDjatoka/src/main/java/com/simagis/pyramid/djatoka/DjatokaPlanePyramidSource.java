package com.simagis.pyramid.djatoka;

import com.simagis.pyramid.AbstractPlanePyramidSource;
import com.simagis.pyramid.PlanePyramidTools;
import gov.lanl.adore.djatoka.DjatokaDecodeParam;
import gov.lanl.adore.djatoka.DjatokaException;
import gov.lanl.adore.djatoka.kdu.jni.KduExtractJNI;
import gov.lanl.adore.djatoka.kdu.jni.KduExtractProcessorJNI;
import gov.lanl.adore.djatoka.util.ImageRecord;
import net.algart.arrays.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.util.NoSuchElementException;

public class DjatokaPlanePyramidSource extends AbstractPlanePyramidSource {

    private final File path;
    private final File debugDir;
    private final int compression;
    private final DjatokaDecodeParam decodeParam;
    private final KduExtractProcessorJNI processor;
    private final int bandCount;
    private final long dimX;
    private final long dimY;
    private final Object lock = new Object();
    private final int numberOfResolutions;
    private final long[][] dimensions;

    public DjatokaPlanePyramidSource(ArrayContext context, File path, int compression)
        throws IOException, DjatokaException
    {
        super(context);
        final ImageRecord imageRecord = new ImageRecord(path.getCanonicalPath());
        final File debugDir = new File(path.getParentFile(), ".debug");
        this.path = path;
        this.debugDir = debugDir.isDirectory() ? debugDir : null;
        this.compression = compression;
        final ImageRecord metadata = new KduExtractJNI().getMetadata(imageRecord);
        System.out.println("DjatokaPlanePyramidSource: " + metadata);
        this.dimX = metadata.getWidth();
        this.dimY = metadata.getHeight();
        this.bandCount = 3;
        this.numberOfResolutions = PlanePyramidTools.numberOfResolutions(dimX, dimY, compression, 1024);
        this.dimensions = new long[numberOfResolutions][3];

        long dimX = this.dimX, dimY = this.dimY;
        for (final long[] dimension : dimensions) {
            dimension[0] = 3;
            dimension[1] = dimX;
            dimension[2] = dimY;
            dimX /= compression;
            dimY /= compression;
        }

        this.decodeParam = new DjatokaDecodeParam();
        //decodeParam.setLevel(0);
        this.processor = new KduExtractProcessorJNI(imageRecord.getImageFile(), decodeParam);
    }

    @Override
    public int numberOfResolutions() {
        return numberOfResolutions;
    }

    @Override
    public int bandCount() {
        return bandCount;
    }

    @Override
    public long[] dimensions(int resolutionLevel) throws NoSuchElementException {
        return dimensions[resolutionLevel].clone();
    }

    @Override
    protected Matrix<? extends PArray> readLittleSubMatrix(
        int resolutionLevel, long fromX, long fromY, long toX, long toY)
    {
        synchronized (lock) {
            try {
                return pyramidLittleSubMatrix0(resolutionLevel, fromX, fromY, toX, toY);
            } catch (DjatokaException e) {
                e.printStackTrace();
                throw new IOError(new IOException(e.getClass().getName() + ": " + e.getMessage()));
            }
        }
    }

    private Matrix<? extends PArray> pyramidLittleSubMatrix0(
        int resolutionLevel, long fromX, long fromY, long toX, long toY) throws DjatokaException
    {
        final long[] dim = dimensions(resolutionLevel);
        final long dimX = dim[1];
        final long dimY = dim[2];

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

        final int levelReductionFactor = resolutionLevel * 2;
        final int fixReduce = 1 << levelReductionFactor;
        final String region = fromY * fixReduce + "," + fromX * fixReduce + "," + sizeY + "," + sizeX;

        decodeParam.setLevelReductionFactor(levelReductionFactor);
        decodeParam.setLevel(levelReductionFactor);
        decodeParam.setRegion(region);
        final BufferedImage image = processor.extract();
        if (debugDir != null) {
            try {
                ImageIO.write(image, "png", new File(debugDir, region + ".png"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        final int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        final byte[] buff = new byte[data.length * bandCount];
        for (int i = 0; i < data.length; i++) {
            final int c = data[i];
            final int k = i * bandCount;
            for (int j = 0; j < bandCount; j++) {
                final int b;
                switch (j) {
                    case 0:
                        b = c >> 16;
                        break;
                    case 1:
                        b = c >> 8;
                        break;
                    case 2:
                        b = c;
                        break;
                    default:
                        throw new AssertionError("Invalid bandCount: " + bandCount);
                }
                buff[k + j] = (byte) (b & 0xff);
            }
        }
        return Matrices.matrix(SimpleMemoryModel.asUpdatableByteArray(buff), bandCount, sizeX, sizeY);
    }

}
