package com.simagis.pyramid.openslide.server;

import com.simagis.pyramid.PlanePyramidSource;
import com.simagis.pyramid.PlanePyramidSourceFactory;
import com.simagis.pyramid.openslide.OpenSlidePlanePyramidSource;

import java.awt.*;
import java.io.File;
import java.io.IOException;

//TODO!! really attach via simagis-live-format.json
//TODO!! create uploader utility OpenSlide2Json
public class OpenSlidePlanePyramidSourceFactory implements PlanePyramidSourceFactory {
    @Override
    public PlanePyramidSource newPlanePyramidSource(
        String pyramidPath,
        String pyramidConfiguration,
        String renderingConfiguration)
        throws IOException
    {
        return new OpenSlidePlanePyramidSource(
            null,
            new File(pyramidPath),
            0,
            Color.WHITE,
            4096,
            0.99);
    }
}
