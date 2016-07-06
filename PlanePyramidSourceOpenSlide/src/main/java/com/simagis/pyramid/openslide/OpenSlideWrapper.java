package com.simagis.pyramid.openslide;

import org.openslide.OpenSlide;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class OpenSlideWrapper {
    final OpenSlide openSlide;

    public OpenSlideWrapper(File file) throws IOException {
        this.openSlide = new OpenSlide(file);
    }

    public int getLevelCount() {
        return openSlide.getLevelCount();
    }

    public long getLevel0Width() {
        return openSlide.getLevel0Width();
    }

    public long getLevel0Height() {
        return openSlide.getLevel0Height();
    }


    public long getLevelWidth(int level) {
        return openSlide.getLevelWidth(level);
    }

    public long getLevelHeight(int level) {
        return openSlide.getLevelHeight(level);
    }

    public void dispose() {
        openSlide.dispose();
    }

    public void paintRegionARGB(int[] dest, long x, long y, int level, int w, int h) throws IOException {
        openSlide.paintRegionARGB(dest, x, y, level, w, h);
    }
}
