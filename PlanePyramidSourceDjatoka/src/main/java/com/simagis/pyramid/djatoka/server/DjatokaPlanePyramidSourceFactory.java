package com.simagis.pyramid.djatoka.server;

import com.simagis.live.json.minimal.SimagisLiveUtils;
import com.simagis.pyramid.PlanePyramidSource;
import com.simagis.pyramid.PlanePyramidSourceFactory;
import com.simagis.pyramid.PlanePyramidTools;
import com.simagis.pyramid.djatoka.DjatokaPlanePyramidSource;
import gov.lanl.adore.djatoka.DjatokaException;

import java.io.File;
import java.io.IOException;

public class DjatokaPlanePyramidSourceFactory implements PlanePyramidSourceFactory {
    @Override
    public PlanePyramidSource newPlanePyramidSource(
        String pyramidPath,
        String pyramidConfiguration,
        String renderingConfiguration)
        throws IOException
    {
        final DjatokaPlanePyramidSource result;
        try {
            result = new DjatokaPlanePyramidSource(new File(pyramidPath));
        } catch (DjatokaException e) {
            throw PlanePyramidTools.rmiSafeWrapper(e);
        }
        SimagisLiveUtils.standardCustomizePlanePyramidSourceRendering(result, renderingConfiguration);
        return result;
    }
}
