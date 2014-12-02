package com.simagis.pyramid.dicom.server;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.image.ColorModelFactory;
import org.dcm4che2.image.VOIUtils;
import org.dcm4che2.imageio.plugins.dcm.DicomStreamMetaData;

import javax.imageio.metadata.IIOMetadata;
import java.awt.image.DataBuffer;

public class DICOMImageIOMetadata {
    final int width;
    final int height;
    final int frames;
    final int allocated;
    final int stored;
    final int dataType;
    final int samples;
    final boolean monochrome;
    final boolean paletteColor;
    final boolean banded;
    final double voiCenter;
    final double voiWidth;

    private DICOMImageIOMetadata(DicomStreamMetaData dicomStreamMetaData, int imageIndex) {
        final DicomObject dicom = dicomStreamMetaData.getDicomObject();
        width = dicom.getInt(Tag.Columns);
        height = dicom.getInt(Tag.Rows);
        frames = dicom.getInt(Tag.NumberOfFrames);
        allocated = dicom.getInt(Tag.BitsAllocated, 8);
        stored = dicom.getInt(Tag.BitsStored, allocated);
        banded = dicom.getInt(Tag.PlanarConfiguration) != 0;
        dataType = allocated <= 8 ? DataBuffer.TYPE_BYTE : DataBuffer.TYPE_USHORT;
        samples = dicom.getInt(Tag.SamplesPerPixel, 1);
        paletteColor = ColorModelFactory.isPaletteColor(dicom);
        monochrome = ColorModelFactory.isMonochrome(dicom);
        DicomObject voiObj = VOIUtils.selectVoiObject(dicom, null, imageIndex + 1);
        voiCenter = voiObj == null ? 0.0 : voiObj.getFloat(Tag.WindowCenter, 0f);
        voiWidth = voiObj == null ? 0.0 : voiObj.getFloat(Tag.WindowWidth, 0f);
    }

    public static DICOMImageIOMetadata getInstance(DicomStreamMetaData metadata, int imageIndex) {
        return new DICOMImageIOMetadata(metadata, imageIndex);
    }

    public static DICOMImageIOMetadata getInstance(IIOMetadata metadata, int imageIndex) {
        if (metadata instanceof DicomStreamMetaData) {
            return new DICOMImageIOMetadata((DicomStreamMetaData) metadata, imageIndex);
        } else {
            return null;
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFrames() {
        return frames;
    }

    public int getAllocated() {
        return allocated;
    }

    public int getStored() {
        return stored;
    }

    public int getDataType() {
        return dataType;
    }

    public int getSamples() {
        return samples;
    }

    public boolean isMonochrome() {
        return monochrome;
    }

    public boolean isPaletteColor() {
        return paletteColor;
    }

    public boolean isBanded() {
        return banded;
    }

    public double getVoiCenter() {
        return voiCenter;
    }

    public double getVoiWidth() {
        return voiWidth;
    }

    @Override
    public String toString() {
        return "DICOMImageIOMetadata{" +
            "width=" + width +
            ", height=" + height +
            ", frames=" + frames +
            ", allocated=" + allocated +
            ", stored=" + stored +
            ", dataType=" + dataType +
            ", samples=" + samples +
            ", monochrome=" + monochrome +
            ", paletteColor=" + paletteColor +
            ", banded=" + banded +
            ", voiCenter=" + voiCenter +
            ", voiWidth=" + voiWidth +
            '}';
    }
}
