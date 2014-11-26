package com.simagis.pyramid.openslide;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class OpenSlideWrapper {
    final Class<?> openSlideClass;
    final Object openSlide;
    final Method getLevelCount;
    final Method getLevel0Width;
    final Method getLevel0Height;
    final Method getLevelWidth;
    final Method getLevelHeight;
    final Method paintRegionARGB;
    final Method dispose;

    public OpenSlideWrapper(File file) throws java.io.IOException {
        Class<?> openSlideClass;
        Method getLevelCount;
        Method getLevel0Width;
        Method getLevel0Height;
        Method getLevelWidth;
        Method getLevelHeight;
        Method paintRegionARGB;
        Method dispose;
        try {
            openSlideClass = Class.forName("edu.cmu.cs.openslide.OpenSlide");
            getLevelCount = openSlideClass.getMethod("getLayerCount");
            getLevel0Width = openSlideClass.getMethod("getLayer0Width");
            getLevel0Height = openSlideClass.getMethod("getLayer0Height");
            getLevelWidth = openSlideClass.getMethod("getLayerWidth", int.class);
            getLevelHeight = openSlideClass.getMethod("getLayerHeight", int.class);
            paintRegionARGB = openSlideClass.getMethod("paintRegionARGB", 
                int[].class, long.class, long.class, int.class, int.class, int.class);
            dispose = openSlideClass.getMethod("dispose"); 
        } catch (ClassNotFoundException e) {
            try {
                openSlideClass = Class.forName("org.openslide.OpenSlide");
                getLevelCount = openSlideClass.getMethod("getLevelCount");
                getLevel0Width = openSlideClass.getMethod("getLevel0Width");
                getLevel0Height = openSlideClass.getMethod("getLevel0Height");
                getLevelWidth = openSlideClass.getMethod("getLevelWidth", int.class);
                getLevelHeight = openSlideClass.getMethod("getLevelHeight", int.class);
                paintRegionARGB = openSlideClass.getMethod("paintRegionARGB", 
                    int[].class, long.class, long.class, int.class, int.class, int.class);
                dispose = openSlideClass.getMethod("dispose"); 
            } catch (ClassNotFoundException ex) {
                throw new IOException("Cannot find OpenSlide library "
                    + "as edu.cmu.cs.openslide.OpenSlide or org.openslide.OpenSlide");
            } catch (NoSuchMethodException ex) {
                throw new IOException("Incompatibility with OpenSlide library", ex);
            }

        } catch (NoSuchMethodException e) {
            throw new IOException("Incompatibility with OpenSlide library", e);
        }
        this.openSlideClass = openSlideClass;
        this.getLevelCount = getLevelCount;
        this.getLevel0Width = getLevel0Width;
        this.getLevel0Height = getLevel0Height;
        this.getLevelWidth = getLevelWidth;
        this.getLevelHeight = getLevelHeight;
        this.paintRegionARGB = paintRegionARGB;
        this.dispose = dispose;
        try {
            Constructor<?> constructor = this.openSlideClass.getConstructor(File.class);
            this.openSlide = constructor.newInstance(file);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("Unexpected exception while OpenSlide constructor call", e);
        } catch (Exception e) {
            throw new IOException("Incompatibility with OpenSlide library", e);
        }
    }

    public int getLevelCount() {
        try {
            return (Integer) getLevelCount.invoke(openSlide);
        } catch (Exception e) {
            throw new AssertionError("Incompatibility with OpenSlide library", e);
        }
    }

    public long getLevel0Width() {
        try {
            return (Long) getLevel0Width.invoke(openSlide);
        } catch (Exception e) {
            throw new AssertionError("Incompatibility with OpenSlide library", e);
        }
    }

    public long getLevel0Height() { 
        try {
            return (Long) getLevel0Height.invoke(openSlide);
        } catch (Exception e) {
            throw new AssertionError("Incompatibility with OpenSlide library", e);
        }
    }
        

    public long getLevelWidth(int layer) {
        try {
            return (Long) getLevelWidth.invoke(openSlide, layer);
        } catch (Exception e) {
            throw new AssertionError("Incompatibility with OpenSlide library", e);
        }
    }

    public long getLevelHeight(int layer) { 
        try {
            return (Long) getLevelHeight.invoke(openSlide, layer);
        } catch (Exception e) {
            throw new AssertionError("Incompatibility with OpenSlide library", e);
        }
    }
    
    public void dispose() {
        try {
            dispose.invoke(openSlide);
        } catch (Exception e) {
            throw new AssertionError("Incompatibility with OpenSlide library", e);
        }
    }
    
    public void paintRegionARGB(int[] dest, long x, long y, int layer, int w, int h) throws java.io.IOException { 
        try {
            paintRegionARGB.invoke(openSlide, dest, x, y, layer, w, h);
        } catch (InvocationTargetException e) {
            throw new IOException(e);
        } catch (Exception e) {
            throw new AssertionError("Incompatibility with OpenSlide library", e);
        }
    }
    

}
