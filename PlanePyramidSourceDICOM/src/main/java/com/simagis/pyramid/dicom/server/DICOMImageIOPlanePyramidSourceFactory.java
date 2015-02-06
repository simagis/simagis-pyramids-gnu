package com.simagis.pyramid.dicom.server;

import net.algart.simagis.live.json.minimal.SimagisLiveUtils;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidSourceFactory;
import net.algart.simagis.pyramid.sources.ImageIOPlanePyramidSource;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class DICOMImageIOPlanePyramidSourceFactory implements PlanePyramidSourceFactory {

    @Override
    public PlanePyramidSource newPlanePyramidSource(
        String pyramidPath,
        String pyramidConfiguration,
        String renderingConfiguration)
        throws IOException
    {
        final JSONObject renderingJson = SimagisLiveUtils.configurationStringToJson(renderingConfiguration);
        final JSONObject dicomRenderingJson = SimagisLiveUtils.openObject(renderingJson, "DICOM");
        final JSONObject centerWindowJson = dicomRenderingJson.optJSONObject("cw");
        boolean pngFormat = renderingJson.optString("format").equals("png");
        final DICOMImageIOReadingBehaviour behaviour = new DICOMImageIOReadingBehaviour();
        behaviour.setAddAlphaWhenExist(pngFormat);
        behaviour.setImageIndex(renderingJson.optInt("imageIndex", behaviour.getImageIndex()));
        behaviour.setRawRasterForMonochrome(dicomRenderingJson.optBoolean("rawRasterForMonochrome",
            behaviour.isRawRasterForMonochrome()));
        if (centerWindowJson != null) {
            behaviour.setAutoWindowing(centerWindowJson.optBoolean("autoWindowing", behaviour.isAutoWindowing()));
            behaviour.setCenter(centerWindowJson.optDouble("center", behaviour.getCenter()));
            behaviour.setWidth(centerWindowJson.optDouble("width", behaviour.getWidth()));
        }
        return new ImageIOPlanePyramidSource(
            null, // i.e. SimpleMemoryModel by default
            null, // no cache
            new File(pyramidPath),
            behaviour);
    }
}
