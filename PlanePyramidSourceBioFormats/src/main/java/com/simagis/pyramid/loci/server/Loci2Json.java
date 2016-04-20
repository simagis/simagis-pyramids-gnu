package com.simagis.pyramid.loci.server;

import com.simagis.pyramid.loci.LociPlanePyramidSource;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import net.algart.arrays.ByteArray;
import net.algart.arrays.Matrices;
import net.algart.arrays.Matrix;
import net.algart.arrays.PArray;
import net.algart.external.MatrixToBufferedImageConverter;
import net.algart.math.functions.LinearFunc;
import net.algart.simagis.live.json.minimal.SimagisLiveUtils;
import net.algart.simagis.pyramid.PlanePyramidSource;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class Loci2Json {
    public static void main(String... args) {
        SimagisLiveUtils.disableStandardOutput();
        try {
            final JSONObject conf = SimagisLiveUtils.parseArgs(args);
            final JSONObject options = conf.getJSONObject("options");
            final File file = new File(conf.getJSONArray("values").getString(0));
            final JSONObject json = new JSONObject();
            final JSONObject result = new JSONObject();
            result.put("formatChecked", true);
            if (LociPlanePyramidSource.isLociFile(file) && !file.getName().toLowerCase().endsWith(".zip")) {
                // Loci tries to "understand" usual zip-files and usually it leads to a strange error message
                final LociPlanePyramidSource source = new LociPlanePyramidSource(null, file, null, null, null);
                final long[] dimensions = source.dimensions(0);
                result.put("dimX", dimensions[PlanePyramidSource.DIM_WIDTH]);
                result.put("dimY", dimensions[PlanePyramidSource.DIM_HEIGHT]);
                final IFormatReader reader = source.getReader();
                final int imageCount = reader.getImageCount();
                final JSONObject multilayerZCT = new JSONObject();
                multilayerZCT.put("imageCount", imageCount);
                JSONArray allZCTs = new JSONArray();
                for (int imageIndex = 0; imageIndex < imageCount; imageIndex++) {
                    final int[] zct = reader.getZCTCoords(imageIndex);
                    JSONObject jsonZCT = new JSONObject();
                    jsonZCT.put("z", zct[0]);
                    jsonZCT.put("c", zct[1]);
                    jsonZCT.put("t", zct[2]);
                    allZCTs.put(jsonZCT);
                }
                multilayerZCT.put("allZCT", allZCTs);
                result.put("multilayerZCT", multilayerZCT);
                result.put("rejected", false);

                if (options.optBoolean("extractLiveProject")) {
                    final File projectDir = new File(options.getString("projectDir"));
                    final File pyramidDir = new File(options.getString("pyramidDir"));
                    final JSONObject liveProject = extractLiveProject(source, projectDir, pyramidDir);
                    if (liveProject == null) {
                        result.put("rejected", true);
                        result.put("message", "Loci cannot read the image");
                    } else {
                        result.put("liveProject", liveProject);
                    }
                }
            } else {
                result.put("rejected", true);
                result.put("message", "Invalid format (non-Loci file)");
            }
            json.put("result", result);
            if (options.optBoolean("getFormats")) {
                JSONArray jsonFormats = new JSONArray();
                for (IFormatReader r : new ImageReader().getReaders()) {
                    final JSONObject jsonFormat = new JSONObject();
                    jsonFormat.put("format", r.getFormat());
                    jsonFormat.put("class", r.getClass().getName());
                    jsonFormat.put("suffixes", r.getSuffixes());
                    jsonFormats.put(jsonFormat);
                }
                json.put("lociFormats", jsonFormats);
            }
            SimagisLiveUtils.OUT.print(json.toString(4));

            System.exit(0);
        } catch (Throwable e) {
            SimagisLiveUtils.printJsonError(e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static JSONObject extractLiveProject(LociPlanePyramidSource source, File projectDir, File pyramidDir)
        throws IOException, JSONException
    {
        final JSONObject result = new JSONObject();
        final JSONArray attributes = new JSONArray();
        result.put("bandCount", source.bandCount());
        SimagisLiveUtils.putRowAttribute(attributes, "bandCount", source.bandCount(), true);
        final IFormatReader reader = source.getReader();
        SimagisLiveUtils.putRowAttribute(attributes, "format", reader.getFormat(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "imageCount", reader.getImageCount(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "seriesCount", reader.getSeriesCount(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "resolutionCount", reader.getResolutionCount(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "sizeX", reader.getSizeX(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "sizeY", reader.getSizeY(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "sizeZ", reader.getSizeZ(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "sizeC", reader.getSizeC(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "effectiveSizeC", reader.getEffectiveSizeC(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "sizeT", reader.getSizeT(), true);
        addMetadata(attributes, reader.getSeriesMetadata(), "seriesMetadata");
        addMetadata(attributes, reader.getGlobalMetadata(), "globalMetadata");
        SimagisLiveUtils.putRowAttribute(attributes, "datasetStructureDescription",
            reader.getDatasetStructureDescription(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "dimensionOrder", reader.getDimensionOrder(), true);
//        SimagisLiveUtils.putRowAttribute(attributes, "16BitLookupTable", reader.get16BitLookupTable(), true);
//        SimagisLiveUtils.putRowAttribute(attributes, "8BitLookupTable", reader.get8BitLookupTable(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "bitsPerPixel", reader.getBitsPerPixel(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "pixelType", reader.getPixelType(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "channelDimLengths", reader.getChannelDimLengths(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "channelDimTypes", reader.getChannelDimTypes(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "rgbChannelCount", reader.getRGBChannelCount(), true);
//        SimagisLiveUtils.putRowAttribute(attributes, "suffixes", reader.getSuffixes(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "thumbSizeX", reader.getThumbSizeX(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "thumbSizeY", reader.getThumbSizeY(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "companionFiles", reader.hasCompanionFiles(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "falseColor", reader.isFalseColor(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "groupFiles", reader.isGroupFiles(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "indexed", reader.isIndexed(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "interleaved", reader.isInterleaved(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "littleEndian", reader.isLittleEndian(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "normalized", reader.isNormalized(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "orderCertain", reader.isOrderCertain(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "rgb", reader.isRGB(), true);
        SimagisLiveUtils.putRowAttribute(attributes, "thumbnailSeries", reader.isThumbnailSeries(), true);
        result.put("attributes", attributes);

        final BufferedImage bufferedImage;
        try {
            Matrix<? extends PArray> label =
                source.readSpecialMatrix(PlanePyramidSource.SpecialImageKind.LABEL_ONLY_IMAGE);
            final MatrixToBufferedImageConverter.Packed3DToPackedRGB converter =
                new MatrixToBufferedImageConverter.Packed3DToPackedRGB(false);
            if (converter.byteArrayRequired() && label.elementType() != byte.class) {
                double max = label.array().maxPossibleValue(1.0);
                label = Matrices.asFuncMatrix(LinearFunc.getInstance(0.0, 255.0 / max), ByteArray.class, label);
            }
            bufferedImage = converter.toBufferedImage(label);
        } catch (RuntimeException e) {
            // Loci image can be unpredictable and lead some errors while conversion,
            // for example, non-supported number of bits etc.; let's ignore such errors
            e.printStackTrace();
            System.err.println("Loci: rejecting image due to the previous error \"" + e + "\"");
            return null;
        }
        SimagisLiveUtils.putImageThumbnailAttribute(attributes, "Label image",
            SimagisLiveUtils.createThumbnail(projectDir, pyramidDir, "label", bufferedImage), true);
        // No sense to add map image in current version: it is the same image.
        // Moreover, no sense to make it non-advanced attribute: some labels are resized to thumbnail
        // incorrectly by createThumbnail (when they are 8-bit).
        return result;
    }

    private static void addMetadata(JSONArray attributes, Map<String, Object> metadata, String metadataName)
        throws JSONException
    {
        if (!metadata.isEmpty()) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                SimagisLiveUtils.putRowAttribute(attributes,
                    metadataName + "." + entry.getKey(), entry.getValue(), true);
            }
        }
    }
}
