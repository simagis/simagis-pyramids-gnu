package com.simagis.pyramid.djatoka.server;

import com.simagis.pyramid.djatoka.DjatokaPlanePyramidSource;
import gov.lanl.adore.djatoka.DjatokaException;
import net.algart.simagis.live.json.minimal.SimagisLiveUtils;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidSourceFactory;
import net.algart.simagis.pyramid.PlanePyramidTools;

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
