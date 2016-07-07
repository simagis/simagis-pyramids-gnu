package com.simagis.pyramid.openslide.server;

import com.simagis.pyramid.openslide.OpenSlidePlanePyramidSource;
import net.algart.simagis.live.json.minimal.SimagisLiveUtils;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidSourceFactory;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class OpenSlidePlanePyramidSourceFactory implements PlanePyramidSourceFactory {
    @Override
    public PlanePyramidSource newPlanePyramidSource(
        String pyramidPath,
        String pyramidConfiguration,
        String renderingConfiguration)
        throws IOException
    {
        final OpenSlidePlanePyramidSource result = new OpenSlidePlanePyramidSource(
            null,
            new File(pyramidPath),
            Color.WHITE,
            4096,
            0.99);
        SimagisLiveUtils.standardCustomizePlanePyramidSourceRendering(result, renderingConfiguration);
        return result;
    }
}
