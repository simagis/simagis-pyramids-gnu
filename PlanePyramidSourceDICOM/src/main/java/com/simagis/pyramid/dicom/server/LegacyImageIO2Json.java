package com.simagis.pyramid.dicom.server;

import net.algart.simagis.imageio.IIOMetadataToJsonConverter;
import net.algart.simagis.live.json.minimal.SimagisLiveUtils;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.*;
import java.util.Iterator;

@Deprecated
class LegacyImageIO2Json {
    public static final PrintStream OUT = System.out;
    public static final OutputStream NULL = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            // NOP
        }
    };

    public void buildJson(String... args) {
        System.setOut(new PrintStream(NULL));
        try {
            final JSONObject conf = SimagisLiveUtils.parseArgs(args);
            final JSONObject options = conf.getJSONObject("options");
            final File file = new File(conf.getJSONArray("values").getString(0));
            if (!file.exists())
                throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
            final JSONObject json = new JSONObject();
            final JSONObject result = new JSONObject();
            final ImageInputStream iis = ImageIO.createImageInputStream(file);
            final Iterator<ImageReader> iterator = ImageIO.getImageReaders(iis);
            if (!iterator.hasNext())
                throw new IIOException("Unknown image format: can't create an ImageInputStream");
            final ImageReader reader = iterator.next();
            final JSONObject imageType = SimagisLiveUtils.openObject(result, "imageType");
            try {
                reader.setInput(iis);
                result.put("formatName", reader.getFormatName());
                final int dimX = reader.getWidth(0);
                final int dimY = reader.getHeight(0);
                if ((long) dimX * (long) dimY >= 512L * 1024L * 1024L) {
                    throw new IIOException("Too large image (more than 512 million pixels)");
                    // We are little reinsured here: Java API can try to create a single byte[]
                    // or short[] array with packed RGB triples (3 * dimX * dimY), and we would like
                    // to be sure that its size will be far from the Java language limit 2 GB.
                }
                result.put("dimX", dimX);
                result.put("dimY", dimY);
                Iterator<ImageTypeSpecifier> iioImageTypes = reader.getImageTypes(0);
                if (iioImageTypes.hasNext()) {
                    ImageTypeSpecifier imageTypeSpecifier = iioImageTypes.next();
                    imageType.put("numComponents", imageTypeSpecifier.getNumComponents());
                    imageType.put("numBands", imageTypeSpecifier.getNumBands());
                }
                final IIOMetadata streamMetadata = reader.getStreamMetadata();
                final IIOMetadataToJsonConverter converter = new IIOMetadataToJsonConverter();
                if (streamMetadata != null) {
                    processStreamMetadata(result, streamMetadata, converter);
                }
                final IIOMetadata imageMetadata = reader.getImageMetadata(0);
                if (imageMetadata != null) {
                    processImageMetadata(result, converter, imageMetadata);
                }
            } finally {
                reader.dispose();
                iis.close();
            }
            if (options.optBoolean("extractLiveProject")) {
                JSONObject liveProject = extractLiveProject(result);
                boolean alpha = imageType.optInt("numComponents") >= 4;
                if (alpha) {
                    liveProject.put("recommendedFormat", "png");
                }
                result.put("liveProject", liveProject);
                if (!options.optBoolean("keepMetadata")) {
                    result.remove("imageMetadata");
                }
            }
            json.put("result", result);
            OUT.print(json.toString(4));
            System.exit(0);
        } catch (Throwable e) {
            printJsonError(e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    protected void processStreamMetadata(
            JSONObject result,
            IIOMetadata streamMetadata,
            IIOMetadataToJsonConverter converter) throws JSONException
    {
        result.put("streamMetadata", converter.toJson(streamMetadata));
    }

    protected void processImageMetadata(
            JSONObject result,
            IIOMetadataToJsonConverter converter,
            IIOMetadata imageMetadata) throws JSONException
    {
        result.put("imageMetadata", converter.toJson(imageMetadata));
    }

    public static void main(String... args) {
        new LegacyImageIO2Json().buildJson(args);
    }

    private static JSONObject extractLiveProject(JSONObject json) throws ScriptException, JSONException, IOException {
        final StringBuffer script = new StringBuffer();
        final char[] buff = new char[1024];
        appendScript("js/json.js", script, buff);
        appendScript("js/jsonpath.js", script, buff);
        appendScript("js/JCM-5000.js", script, buff);
        script.append(String.format("%n extractLiveProject(%s);", json.toString(4)));
        final ScriptEngineManager engineManager = new ScriptEngineManager();
        final ScriptEngine javaScript = engineManager.getEngineByName("JavaScript");
        final Object result = javaScript.eval(script.toString());
        if (!(result instanceof String)) throw new ScriptException("Invalid script result type");
        return new JSONObject((String) result);
    }

    private static void appendScript(String name, StringBuffer script, char[] buff) throws IOException {
        final InputStream inputStream = LegacyImageIO2Json.class.getResourceAsStream(name);
        final InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
        try {
            while (true) {
                final int read = reader.read(buff);
                if (read == -1) break;
                script.append(buff, 0, read);
            }
        } finally {
            reader.close();
        }
        script.append(String.format("%n"));
    }

    private static void printJsonError(Throwable e) {
        String error;
        try {
            error = SimagisLiveUtils.putError(new JSONObject(), e).toString(4);
        } catch (JSONException e1) {
            e.addSuppressed(e1);
            error = SimagisLiveUtils.putError(new JSONObject(), e).toString();
        }
        OUT.print(error);
    }
}
