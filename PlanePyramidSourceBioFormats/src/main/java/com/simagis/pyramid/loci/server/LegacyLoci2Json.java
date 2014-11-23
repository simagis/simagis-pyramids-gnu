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

package com.simagis.pyramid.loci.server;

import com.simagis.live.json.minimal.SimagisLiveUtils;
import com.simagis.pyramid.PlanePyramidSource;
import com.simagis.pyramid.loci.LociPlanePyramidSource;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import net.algart.arrays.Matrix;
import net.algart.arrays.PArray;
import net.algart.external.MatrixToBufferedImageConverter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

@Deprecated
public class LegacyLoci2Json {
    public static void main(String... args) {
        SimagisLiveUtils.disableStandardOutput();
        try {
            final JSONObject json = new JSONObject();
            final JSONObject conf = SimagisLiveUtils.parseArgs(args);
            final JSONObject options = conf.getJSONObject("options");
            if (options.optBoolean("getFormats")) {
                JSONArray jsonFormats = new JSONArray();
                for (IFormatReader r : new ImageReader().getReaders()) {
                    final JSONObject jsonLoci = new JSONObject();
                    jsonLoci.put("format", r.getFormat());
                    jsonLoci.put("class", r.getClass().getName());
                    jsonLoci.put("suffixes", r.getSuffixes());
                    jsonFormats.put(jsonLoci);
                }
                json.put("lociFormats", jsonFormats);
            } else {
                final File file = new File(conf.getJSONArray("values").getString(0));
                final LociPlanePyramidSource source = new LociPlanePyramidSource(
                    null, file, options.optString("reader", null), null, null);
                final JSONObject result = new JSONObject();
                final long[] dimensions = source.dimensions(0);
                result.put("bandCount", source.bandCount());
                result.put("dimX", dimensions[1]);
                result.put("dimY", dimensions[2]);
                final JSONObject jsonLoci = new JSONObject();
                // more automatic call "new JSONObject(reader);" leads to errors in Loci
                final IFormatReader reader = source.getReader();
                jsonLoci.put("format", reader.getFormat());
                final int imageCount = reader.getImageCount();
                jsonLoci.put("imageCount", imageCount);
                jsonLoci.put("seriesCount", reader.getSeriesCount());
                jsonLoci.put("resolutionCount", reader.getResolutionCount());
                jsonLoci.put("sizeX", reader.getSizeX());
                jsonLoci.put("sizeY", reader.getSizeY());
                jsonLoci.put("sizeZ", reader.getSizeZ());
                jsonLoci.put("sizeC", reader.getSizeC());
                jsonLoci.put("effectiveSizeC", reader.getEffectiveSizeC());
                jsonLoci.put("sizeT", reader.getSizeT());
                jsonLoci.put("seriesMetadata", reader.getSeriesMetadata());
                jsonLoci.put("globalMetadata", reader.getGlobalMetadata());
                jsonLoci.put("datasetStructureDescription", reader.getDatasetStructureDescription());
                jsonLoci.put("dimensionOrder", reader.getDimensionOrder());
//                jsonLoci.put("16BitLookupTable", reader.get16BitLookupTable());
//                jsonLoci.put("8BitLookupTable", reader.get8BitLookupTable());
                jsonLoci.put("bitsPerPixel", reader.getBitsPerPixel());
                jsonLoci.put("pixelType", reader.getPixelType());
                jsonLoci.put("channelDimLengths", reader.getChannelDimLengths());
                jsonLoci.put("channelDimTypes", reader.getChannelDimTypes());
                jsonLoci.put("rgbChannelCount", reader.getRGBChannelCount());
                jsonLoci.put("suffixes", reader.getSuffixes());
                jsonLoci.put("thumbSizeX", reader.getThumbSizeX());
                jsonLoci.put("thumbSizeY", reader.getThumbSizeY());
                jsonLoci.put("companionFiles", reader.hasCompanionFiles());
                jsonLoci.put("falseColor", reader.isFalseColor());
                jsonLoci.put("groupFiles", reader.isGroupFiles());
                jsonLoci.put("indexed", reader.isIndexed());
                jsonLoci.put("interleaved", reader.isInterleaved());
                jsonLoci.put("littleEndian", reader.isLittleEndian());
                jsonLoci.put("normalized", reader.isNormalized());
                jsonLoci.put("orderCertain", reader.isOrderCertain());
                jsonLoci.put("rgb", reader.isRGB());
                jsonLoci.put("thumbnailSeries", reader.isThumbnailSeries());
                JSONArray jsonZCTs = new JSONArray();
                for (int imageIndex = 0; imageIndex < imageCount; imageIndex++) {
                    final int[] zct = reader.getZCTCoords(imageIndex);
                    JSONObject jsonZCT = new JSONObject();
                    jsonZCT.put("z", zct[0]);
                    jsonZCT.put("c", zct[1]);
                    jsonZCT.put("t", zct[2]);
                    jsonZCTs.put(jsonZCT);
                }
                jsonLoci.put("allZCT", jsonZCTs);
                result.put("loci", jsonLoci);
                if (options.optBoolean("extractLiveProject")) {
                    final File projectDir = new File(options.getString("projectDir"));
                    final File pyramidDir = new File(options.getString("pyramidDir"));
                    result.put("liveProject", extractLiveProject(source, projectDir, pyramidDir));
                }
                json.put("result", result);
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
        final Matrix<? extends PArray> label =
            source.readSpecialMatrix(PlanePyramidSource.SpecialImageKind.LABEL_ONLY_IMAGE);
        final Matrix<? extends PArray> map =
            source.readSpecialMatrix(PlanePyramidSource.SpecialImageKind.MAP_IMAGE);
        final MatrixToBufferedImageConverter.Packed3DToPackedRGB converter =
            new MatrixToBufferedImageConverter.Packed3DToPackedRGB(false);

        final JSONObject result = new JSONObject();
        final JSONArray attributes = new JSONArray();
        result.put("attributes", attributes);

        SimagisLiveUtils.putImageThumbnailAttribute(attributes, "Label image",
            SimagisLiveUtils.createThumbnail(projectDir, pyramidDir, "label", converter.toBufferedImage(label)), false);
        SimagisLiveUtils.putImageThumbnailAttribute(attributes, "Map image",
            SimagisLiveUtils.createThumbnail(projectDir, pyramidDir, "map", converter.toBufferedImage(map)), true);

        return result;
    }
}
