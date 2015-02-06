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

import com.simagis.pyramid.loci.LociPlanePyramidSource;
import loci.formats.FormatException;
import net.algart.simagis.live.json.minimal.SimagisLiveUtils;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidSourceFactory;
import net.algart.simagis.pyramid.PlanePyramidTools;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class LociPlanePyramidSourceFactory implements PlanePyramidSourceFactory {
    @Override
    public PlanePyramidSource newPlanePyramidSource(
        String pyramidPath,
        String pyramidConfiguration,
        String renderingConfiguration)
        throws IOException
    {
        final JSONObject pyramidJson = SimagisLiveUtils.configurationStringToJson(pyramidConfiguration);
        final JSONObject lociJson = SimagisLiveUtils.openObject(pyramidJson, "format", "loci");
        final JSONObject multilayerZCT = SimagisLiveUtils.openObject(pyramidJson, "multilayerZCT");
        final String fileName = pyramidJson.optString("fileName");
        if (fileName == null) {
            throw new IOException("fileName is not specified in pyramid configuration file (required by Loci)");
        }
        File path = new File(pyramidPath);
        if (pyramidJson.optBoolean("externalData", false)) {
            // necessary to support very old formats
            final String dataLayer = pyramidJson.optString("dataLayer");
            if (dataLayer == null) {
                throw new IOException("dataLayer is not specified in pyramid configuration file");
            }
            path = path.getParentFile(); // like "p-8c2bd6e8-1bc8-4a12-8445-30721c18ca16"
            path = path.getParentFile(); // like "image01"
            path = path.getParentFile(); // like "dfb3c310-0307-4c2d-abc1-f649b82a70a5" (project dir)
            path = new File(path, dataLayer);
            path = new File(path, "data");
            path = new File(path, fileName);
        }
        int imagePlaneIndex = multilayerZCT.has("imagePlaneIndex")
            ? multilayerZCT.optInt("imagePlaneIndex") : lociJson.has("imagePlaneIndex")
            ? lociJson.optInt("imagePlaneIndex") : -1;
        // - lociJson.optInt above necessary to support formats before 28.Oct.2014
        LociPlanePyramidSource result;
        try {
            result = new LociPlanePyramidSource(null, path);
        } catch (FormatException e) {
            throw PlanePyramidTools.rmiSafeWrapper(e);
        }
        result.setNormalizeFractionalNumberOfBytesPerPixel(lociJson.optBoolean("normalizeFractionalNumberOfBytes",
            result.isNormalizeFractionalNumberOfBytesPerPixel()));
        result.setAutoContrastIfMoreThan8Bits(lociJson.optBoolean("autoContrastIfMoreThan8Bits",
            result.isAutoContrastIfMoreThan8Bits()));
        result.setAutoContrastAlways(lociJson.optBoolean("autoContrast",
            result.isAutoContrastAlways()));
        if (imagePlaneIndex != -1) {
            result.setImagePlaneIndex(imagePlaneIndex);
        }
        return result;
    }
}
