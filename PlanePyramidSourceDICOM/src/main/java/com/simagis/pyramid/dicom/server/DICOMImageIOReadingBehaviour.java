package com.simagis.pyramid.dicom.server;

import com.simagis.pyramid.PlanePyramidSource;
import com.simagis.pyramid.sources.ImageIOPlanePyramidSource;
import org.dcm4che2.imageio.plugins.dcm.DicomImageReadParam;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.IOException;
import java.util.Iterator;

final class DICOMImageIOReadingBehaviour extends ImageIOPlanePyramidSource.ImageIOReadingBehaviour {

    private boolean rawRasterForMonochrome = false; // if true and image is monochrome, we use readRaster
    private boolean autoWindowing = true; // should be true for auto-detection of both center and width
    private double center = 0.0;
    private double width = 0.0; // should be 0.0 for auto-detection of both center and width

    public DICOMImageIOReadingBehaviour() {
        setDicomReader(true);
    }

    public boolean isRawRasterForMonochrome() {
        return rawRasterForMonochrome;
    }

    public void setRawRasterForMonochrome(boolean rawRasterForMonochrome) {
        this.rawRasterForMonochrome = rawRasterForMonochrome;
    }

    public boolean isAutoWindowing() {
        return autoWindowing;
    }

    public void setAutoWindowing(boolean autoWindowing) {
        this.autoWindowing = autoWindowing;
    }

    public double getCenter() {
        return center;
    }

    public void setCenter(double center) {
        this.center = center;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    @Override
    public ImageIOPlanePyramidSource.ImageIOReadingBehaviour setDicomReader(boolean dicomReader) {
        if (!dicomReader)
            throw new IllegalArgumentException("dicomReader must be always true in " + getClass());
        return super.setDicomReader(dicomReader);
    }

    @Override
    protected DicomImageReadParam getReadParam(ImageReader imageReader) {
        final ImageReadParam defaultReadParam = imageReader.getDefaultReadParam();
        if (!(defaultReadParam instanceof DicomImageReadParam))
            throw new AssertionError("imageReader.getDefaultReadParam() is not DicomImageReadParam");
        DicomImageReadParam param = (DicomImageReadParam) defaultReadParam;
        param.setAutoWindowing(autoWindowing);
        param.setWindowCenter((float) center);
        param.setWindowWidth((float) width);
        return param;
    }

    @Override
    protected BufferedImage readBufferedImageByReader(ImageReader reader, ImageReadParam param) throws IOException {
        final DICOMImageIOMetadata dicomImageIOMetadata =
            DICOMImageIOMetadata.getInstance(reader.getStreamMetadata(), imageIndex);
        if (PlanePyramidSource.DEBUG_LEVEL >= 1) {
            System.out.println(DICOMImageIOReadingBehaviour.class.getSimpleName()
                + " will read DICOM image with the following metadata: " + dicomImageIOMetadata);
        }

        if (!rawRasterForMonochrome) {
            return super.readBufferedImageByReader(reader, param);
        }
        Iterator<ImageTypeSpecifier> iioImageTypes = reader.getImageTypes(imageIndex);
        if (iioImageTypes == null) { // not correct, but occurs in dcm4che
            return super.readBufferedImageByReader(reader, param);
        }
        if (!iioImageTypes.hasNext()) {
            return super.readBufferedImageByReader(reader, param);
        }
        ImageTypeSpecifier imageTypeSpecifier = iioImageTypes.next();
        boolean monochrome = imageTypeSpecifier.getNumComponents() == 1 && imageTypeSpecifier.getNumBands() == 1
            && (dicomImageIOMetadata != null && dicomImageIOMetadata.isMonochrome());
        if (!monochrome) {
            return super.readBufferedImageByReader(reader, param);
        }
        final Raster raster = reader.readRaster(imageIndex, param);
        final SampleModel sampleModel = raster.getSampleModel();
        if (!(sampleModel instanceof ComponentSampleModel)) { // we don't know what to do
            return super.readBufferedImageByReader(reader, param);
        }
        WritableRaster wr = Raster.createBandedRaster(
            raster.getDataBuffer(),
            raster.getWidth(),
            raster.getHeight(),
            ((ComponentSampleModel) sampleModel).getScanlineStride(),
            new int[]{0},
            new int[]{0},
            null);
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ComponentColorModel cm = new ComponentColorModel(cs, null,
            false, false, ColorModel.OPAQUE, raster.getTransferType());
        return new BufferedImage(cm, wr, false, null);
    }

    @Override
    public String toString() {
        return "DICOMImageIOReadingBehaviour{"
            + "imageIndex=" + getImageIndex()
            + ", addAlphaWhenExist=" + isAddAlphaWhenExist()
            + ", readPixelValuesViaColorModel=" + isReadPixelValuesViaColorModel()
            + ", readPixelValuesViaGraphics2D=" + isReadPixelValuesViaGraphics2D()
            + ", rawRasterForMonochrome=" + rawRasterForMonochrome
            + ", autoWindowing=" + autoWindowing
            + ", center=" + center
            + ", width=" + width
            + '}';
    }
}
