package com.simagis.pyramid.dicom;

import net.algart.external.ExternalAlgorithmCaller;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

class DICOMImageIO2Jpeg {
    static final boolean READ_VIA_COMMON_IMAGE_IO = false;

    public static void main(String[] args) throws IOException {
        Iterator<ImageReader> iterator = ImageIO.getImageReadersByFormatName("DICOM");
        if (!iterator.hasNext()) {
            System.err.println("No DICOM image reader!");
            System.exit(1);
        }
        if (args.length < 2) {
            System.out.println("Usage: " + DICOMImageIO2Jpeg.class.getName() + " DICOM-file jpeg-file.jpg");
            return;
        }
        File f = new File(args[0]);
        BufferedImage image;
        if (READ_VIA_COMMON_IMAGE_IO) {
            image = ImageIO.read(f);
        } else {
            ImageReader reader = iterator.next();
            ImageInputStream iis = ImageIO.createImageInputStream(f);
            ImageReadParam param = reader.getDefaultReadParam();
            reader.setInput(iis, false);
            System.out.printf("%d images found%n", reader.getNumImages(true));
            image = reader.read(0, param);
            iis.close();
        }
        if (image == null)
            throw new IOException("Cannot read " + f);
        File output = new File(args[1]);
        ImageIO.write(image, ExternalAlgorithmCaller.getFileExtension(output), output);
        System.out.printf("Image %s (%dx%d, %d bands, %d bits/element) converted%n",
            f,
            image.getWidth(),
            image.getHeight(),
            image.getSampleModel().getNumBands(),
            image.getSampleModel().getSampleSize(0));
    }
}
