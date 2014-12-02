package com.simagis.pyramid.dicom;

import com.simagis.live.json.minimal.SimagisLiveUtils;
import org.dcm4che2.data.*;
import org.dcm4che2.imageio.plugins.dcm.DicomStreamMetaData;
import org.dcm4che2.imageioimpl.plugins.dcm.DicomImageWriterSpi;
import org.dcm4che2.media.DicomDirReader;
import org.dcm4che2.media.FileSetInformation;
import org.dcm4che2.util.UIDUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * User: alexei.vylegzhanin@gmail.com
 * Date: 01.05.13
 */
public class DcmDir2DcmFile {
    public static void main(String... args) throws JSONException, IOException {
        final JSONObject config = SimagisLiveUtils.parseArgs(args);
        final JSONArray values = config.getJSONArray("values");
        final File dicomDir = new File(values.getString(0));
        final File dicomFile = new File(values.getString(1));
        final DcmDir2DcmFile dcmFile = new DcmDir2DcmFile();
        dcmFile.test(dicomDir);
        //dcmFile.encodeMultiframe(dicomDir, dicomFile);
    }

    private void test(File dicomDir) throws IOException {
        final DicomDirReader dicomDirReader = new DicomDirReader(dicomDir);
        final FileSetInformation fileSetInformation = dicomDirReader.getFileSetInformation();
        final DicomObject rootRecord = dicomDirReader.findFirstRootRecord();

        for (DicomObject childRecord = dicomDirReader.findNextSiblingRecord(rootRecord);
             childRecord != null;
             childRecord = dicomDirReader.findNextSiblingRecord(childRecord))
        {
            System.out.println(childRecord);

            for (DicomObject childRecord2 = dicomDirReader.findFirstChildRecord(childRecord);
                 childRecord2 != null;
                 childRecord2 = dicomDirReader.findNextSiblingRecord(childRecord2))
            {
                System.out.println("\t" + childRecord2);
            }
        }

    }


    /**
     * Create the DICOM multiframe file header.
     *
     * @param sampleFrame    a sample BufferedImage to get image information.
     * @param numberOfFrames the number of frames of this multiframe DICOM file.
     */
    public DicomObject createDicomHeader(BufferedImage sampleFrame, int numberOfFrames) {

        // Get some image information from the sample image:
        // All frames should have the same information so we will get it only once.
        int colorComponents = sampleFrame.getColorModel().getNumColorComponents();
        int bitsPerPixel = sampleFrame.getColorModel().getPixelSize();
        int bitsAllocated = (bitsPerPixel / colorComponents);
        int samplesPerPixel = colorComponents;

        // The DICOM object that will hold our frames
        DicomObject dicom = new BasicDicomObject();

        // Add patient related information to the DICOM dataset
        dicom.putString(Tag.PatientName, null, "SAMUCS^DEV");
        dicom.putString(Tag.PatientID, null, "1234ID");
        dicom.putDate(Tag.PatientBirthDate, null, new java.util.Date());
        dicom.putString(Tag.PatientSex, null, "M");

        // Add study related information to the DICOM dataset
        dicom.putString(Tag.AccessionNumber, null, "1234AC");
        dicom.putString(Tag.StudyID, null, "1");
        dicom.putString(Tag.StudyDescription, null, "MULTIFRAME STUDY");
        dicom.putDate(Tag.StudyDate, null, new java.util.Date());
        dicom.putDate(Tag.StudyTime, null, new java.util.Date());

        // Add series related information to the DICOM dataset
        dicom.putInt(Tag.SeriesNumber, null, 1);
        dicom.putDate(Tag.SeriesDate, null, new java.util.Date());
        dicom.putDate(Tag.SeriesTime, null, new java.util.Date());
        dicom.putString(Tag.SeriesDescription, null, "MULTIFRAME SERIES");
        dicom.putString(Tag.Modality, null, "SC"); // secondary capture

        // Add image related information to the DICOM dataset
        dicom.putInt(Tag.InstanceNumber, null, 1);
        dicom.putInt(Tag.SamplesPerPixel, null, samplesPerPixel);
        dicom.putString(Tag.PhotometricInterpretation, VR.CS, "YBR_FULL_422");
        dicom.putInt(Tag.Rows, null, sampleFrame.getHeight());
        dicom.putInt(Tag.Columns, null, sampleFrame.getWidth());
        dicom.putInt(Tag.BitsAllocated, null, bitsAllocated);
        dicom.putInt(Tag.BitsStored, null, bitsAllocated);
        dicom.putInt(Tag.HighBit, null, bitsAllocated - 1);
        dicom.putInt(Tag.PixelRepresentation, null, 0);

        // Add the unique identifiers
        dicom.putString(Tag.SOPClassUID, null, UID.SecondaryCaptureImageStorage);
        dicom.putString(Tag.StudyInstanceUID, null, UIDUtils.createUID());
        dicom.putString(Tag.SeriesInstanceUID, null, UIDUtils.createUID());
        dicom.putString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());

        //Start of multiframe information:
        dicom.putInt(Tag.StartTrim, null, 1);                   // Start at frame 1
        dicom.putInt(Tag.StopTrim, null, numberOfFrames);       // Stop at frame N
        dicom.putString(Tag.FrameTime, null, "33.33");          // Milliseconds (30 frames per second)
        dicom.putString(Tag.FrameDelay, null, "0.0");           // No frame dalay
        dicom.putInt(Tag.NumberOfFrames, null, numberOfFrames); // The number of frames
        dicom.putInt(Tag.RecommendedDisplayFrameRate, null, 3);
        dicom.putInt(Tag.FrameIncrementPointer, null, Tag.FrameTime);
        //End of multiframe information.

        // Add the default character set
        dicom.putString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100");

        // Init the meta information with JPEG Lossless transfer syntax
        dicom.initFileMetaInformation(UID.JPEGLossless);

        return dicom;
    }


    /**
     * Encode the extracted FFMPEG frames to DICOM Sequence instances.
     */
    public void encodeMultiframe(File dicomDir, File dicomFile)
        throws IOException
    {


        // Create DICOM image writer instance and set its output
        ImageWriter writer = new DicomImageWriterSpi().createWriterInstance();
        FileImageOutputStream output = new FileImageOutputStream(dicomFile);
        writer.setOutput(output);

        // Get an image sample from the array of images
        File[] frames = null;
        BufferedImage sample = ImageIO.read(frames[0]);

        // Create a new dataset (header/metadata) for our DICOM image writer
        DicomObject ds = this.createDicomHeader(sample, frames.length);

        // Set the metadata to our DICOM image writer and prepare to encode the multiframe sequence
        DicomStreamMetaData writeMeta = (DicomStreamMetaData) writer.getDefaultStreamMetadata(null);
        writeMeta.setDicomObject(ds);
        writer.prepareWriteSequence(writeMeta);

        // Status message
        System.out.println("Start of Write Sequence...");

        // For each extracted FFMPEG images...
        for (int i = 0; i < frames.length; i++) {

            // Status message
            System.out.println("Encoding frame # " + (i + 1));

            // Read the PNG file to a BufferedImage object
            BufferedImage frame = ImageIO.read(frames[i]);

            // Create a new IIOImage to be saved to the DICOM multiframe sequence
            IIOImage iioimage = new IIOImage(frame, null, null);

            // Write our image to the DICOM multiframe sequence
            writer.writeToSequence(iioimage, null);
        }

        // Status message
        System.out.println("End of Write Sequence.");

        // Our multiframe file was created. End the sequence and close the output stream.
        writer.endWriteSequence();
        output.close();

        // Status message
        System.out.println("Multiframe File Created.");
    }


}
