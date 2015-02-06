package com.simagis.pyramid.dicom.server;

import net.algart.simagis.imageio.IIOMetadataToJsonConverter;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.metadata.IIOMetadata;

public class LegacyDICOMImageIO2Json extends LegacyImageIO2Json {
    @Override
    protected void processStreamMetadata(
        JSONObject result,
        IIOMetadata streamMetadata,
        IIOMetadataToJsonConverter converter)
        throws JSONException
    {
        DICOMImageIOMetadata imageIOMetadata = DICOMImageIOMetadata.getInstance(streamMetadata, 0);
        if (imageIOMetadata != null) {
            result.put("DICOMMetadata", new JSONObject(imageIOMetadata));
        } else {
            super.processStreamMetadata(result, streamMetadata, converter);
        }
    }

    public static void main(String[] args) {
        new LegacyDICOMImageIO2Json().buildJson(args);
    }
}
