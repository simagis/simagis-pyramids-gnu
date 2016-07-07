package com.simagis.pyramid.openslide.server;

import com.simagis.pyramid.openslide.OpenSlidePlanePyramidSource;
import net.algart.simagis.live.json.minimal.SimagisLiveUtils;
import net.algart.simagis.pyramid.PlanePyramidSource;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class OpenSlide2Json {
    public static void main(String... args) {
        SimagisLiveUtils.disableStandardOutput();
        try {
            final JSONObject conf = SimagisLiveUtils.parseArgs(args);
            final JSONObject options = conf.getJSONObject("options");
            final File file = new File(conf.getJSONArray("values").getString(0));
            final OpenSlidePlanePyramidSource source = new OpenSlidePlanePyramidSource(file);
            final JSONObject json = new JSONObject();
            final JSONObject result = new JSONObject();
            result.put("dimX", source.getDimX());
            result.put("dimY", source.getDimY());
            result.remove("fileName");
            json.put("result", result);

            if (options.optBoolean("extractLiveProject")) {
                result.put("liveProject", extractLiveProject(source));
            }
            SimagisLiveUtils.OUT.print(json.toString(4));
            System.exit(0);
        } catch (Throwable e) {
            SimagisLiveUtils.printJsonError(e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static JSONObject extractLiveProject(OpenSlidePlanePyramidSource source)
        throws IOException, JSONException
    {
        final JSONObject result = new JSONObject();
        // TODO!! add some attributes
        return result;
    }
}
