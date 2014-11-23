package com.simagis.pyramid.loci;

import loci.formats.IFormatReader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Deprecated
class IFormatReaderCompatibilityWrapper {
    private final IFormatReader parent;
    private final Method hasFlattenedResolutions;
    private final Method setFlattenedResolutions;
    private final Method getResolutionCount;
    private final Method getResolution;
    private final Method setResolution;
    private final boolean newVersion;

    IFormatReaderCompatibilityWrapper(IFormatReader parent) {
        assert parent != null;
        this.parent = parent;
        Method hasFlattenedResolutions;
        Method setFlattenedResolutions;
        Method getResolutionCount;
        Method getResolution;
        Method setResolution;
        boolean newVersion;
        try {
            hasFlattenedResolutions = parent.getClass().getMethod("hasFlattenedResolutions");
            setFlattenedResolutions = parent.getClass().getMethod("setFlattenedResolutions", boolean.class);
            getResolutionCount = parent.getClass().getMethod("getResolutionCount");
            getResolution = parent.getClass().getMethod("getResolution");
            setResolution = parent.getClass().getMethod("setResolution", int.class);
            newVersion = true;
        } catch (NoSuchMethodException e) {
            hasFlattenedResolutions = null; // no such methods in old versions of loci_tools
            setFlattenedResolutions = null;
            getResolutionCount = null;
            getResolution = null;
            setResolution = null;
            newVersion = false;
        }
        this.hasFlattenedResolutions = hasFlattenedResolutions;
        this.setFlattenedResolutions = setFlattenedResolutions;
        this.getResolutionCount = getResolutionCount;
        this.getResolution = getResolution;
        this.setResolution = setResolution;
        this.newVersion = newVersion;
    }

    public IFormatReader parent() {
        return parent;
    }

    public boolean newVersion() {
        return newVersion;
    }

    public boolean hasFlattenedResolutions() {
        assert newVersion;
        try {
            return (Boolean) hasFlattenedResolutions.invoke(parent);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Strange error while calling hasFlattenedResolutions", e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Strange error while calling hasFlattenedResolutions", e);
        }
    }

    /**
     * Does nothing if it is an old version.
     * @param flatten new value of flatting flag.
     */
    public void setFlattenedResolutions(boolean flatten) {
        if (!newVersion) {
            return;
        }
        try {
            setFlattenedResolutions.invoke(parent, flatten);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Strange error while calling setFlattenedResolutions", e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Strange error while calling setFlattenedResolutions", e);
        }
    }

    /**
     * Returns 1 if it is an old version.
     * @return the number of resolutions.
     */
    public int getResolutionCount() {
        if (!newVersion) {
            return 1;
        }
        try {
            return (Integer) getResolutionCount.invoke(parent);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Strange error while calling getResolutionCount", e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Strange error while calling getResolutionCount", e);
        }
    }

    public int getResolution() {
        assert newVersion;
        try {
            return (Integer) getResolution.invoke(parent);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Strange error while calling getResolution", e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Strange error while calling getResolution", e);
        }
    }

    public void setResolution(int resolution) {
        assert newVersion;
        try {
            setResolution.invoke(parent, resolution);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Strange error while calling setResolution", e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Strange error while calling setResolution", e);
        }
    }
}
