package com.simagis.pyramid.dicom.server;

import com.simagis.imageio.IIOMetadataToJsonConverter;
import com.simagis.live.json.minimal.SimagisLiveUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

public class DICOM2Json {
    public static void main(String... args) {
        SimagisLiveUtils.disableStandardOutput();
        try {
            final JSONObject conf = SimagisLiveUtils.parseArgs(args);
            final JSONObject options = conf.getJSONObject("options");
            final File file = new File(conf.getJSONArray("values").getString(0));
            if (!file.exists()) {
                throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
            }
            final JSONObject json = new JSONObject();
            final JSONObject result = getResultJson(file, options.optBoolean("extractLiveProject"));
            json.put("result", result);
            SimagisLiveUtils.OUT.print(json.toString(4));
            System.exit(0);
        } catch (Throwable e) {
            SimagisLiveUtils.printJsonError(e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static JSONObject getResultJson(File file, boolean extractLiveProject)
        throws IOException, JSONException, ScriptException
    {
        final JSONObject result = new JSONObject();
        result.put("formatChecked", true);
        final ImageInputStream iis = ImageIO.createImageInputStream(file);
        final Iterator<ImageReader> iterator = ImageIO.getImageReaders(iis);
        if (!iterator.hasNext()) {
            result.put("rejected", true);
            result.put("message", "Unknown image format: can't create an ImageInputStream");
            return result;
        }
        final ImageReader reader = iterator.next();
        final JSONObject imageType = SimagisLiveUtils.openObject(result, "imageType");
        try {
            reader.setInput(iis);
            JSONObject formatSpecific = new JSONObject();
            formatSpecific.put("javaFormatType", "DICOM");
            formatSpecific.put("javaFormatName", reader.getFormatName());
            result.put("formatSpecific", formatSpecific);

            final int dimX = reader.getWidth(0);
            final int dimY = reader.getHeight(0);
            if ((long) dimX * (long) dimY >= 512L * 1024L * 1024L) {
                result.put("rejected", true);
                result.put("message", "Too large image (more than 512 million pixels)");
                return result;
                // We are little reinsuring here: Java API can try to create a single byte[]
                // or short[] array with packed RGB triples (3 * dimX * dimY), and we would like
                // to be sure that its size will be far from the Java language limit 2 GB.
            }
            result.put("dimX", dimX);
            result.put("dimY", dimY);
            final Iterator<ImageTypeSpecifier> iioImageTypes = reader.getImageTypes(0);
            if (iioImageTypes != null && iioImageTypes.hasNext()) {
                // some 3rd party implementation can still return null here, though it is prohibited in JavaDoc
                ImageTypeSpecifier imageTypeSpecifier = iioImageTypes.next();
                imageType.put("numComponents", imageTypeSpecifier.getNumComponents());
                imageType.put("numBands", imageTypeSpecifier.getNumBands());
                if (imageTypeSpecifier.getNumComponents() >= 4) {
                    result.put("recommendedRenderingFormat", "png");
                }
            }
            final IIOMetadata streamMetadata = reader.getStreamMetadata();
            final IIOMetadataToJsonConverter converter = new IIOMetadataToJsonConverter();
            if (streamMetadata != null) {
                DICOMImageIOMetadata imageIOMetadata = DICOMImageIOMetadata.getInstance(streamMetadata, 0);
                if (imageIOMetadata != null) {
                    result.put("DICOMMetadata", new JSONObject(imageIOMetadata));
                }
                result.put("streamMetadata", converter.toJson(streamMetadata));
            }
            final IIOMetadata imageMetadata = reader.getImageMetadata(0);
            if (imageMetadata != null) {
                result.put("imageMetadata", converter.toJson(imageMetadata));
            }
        } finally {
            reader.dispose();
            iis.close();
        }
        if (extractLiveProject) {
            JSONObject liveProject = extractLiveProject(result);
            result.put("liveProject", liveProject);
            result.remove("imageMetadata");
            result.remove("streamMetadata");
        }
        result.put("rejected", false);
        return result;
    }

    private static JSONObject extractLiveProject(JSONObject resultJson)
        throws ScriptException, JSONException, IOException
    {
        final JSONObject dicomMetadata = resultJson.optJSONObject("DICOMMetadata");
        if (dicomMetadata != null) {
            JSONObject liveProject = new JSONObject();
            final JSONArray attributes = new JSONArray();
            for (String key : SimagisLiveUtils.getKeySet(dicomMetadata)) {
                final Object value = dicomMetadata.get(key);
                SimagisLiveUtils.putRowAttribute(attributes, "DICOM." + key, value, true);
            }
            liveProject.put("attributes", attributes);
            return liveProject;
        } else {
            return null;
        }
    }
}
