/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public class ImageUtil {

    public static BufferedImage getScaledInstance(
            BufferedImage img,
            int targetWidth,
            int targetHeight,
            Object hint, 
            boolean higherQuality) {
        
        if (img == null) {
            return null;
        }
        
        int currImgWidth = img.getWidth();
        int currImgHeight = img.getHeight();
        targetWidth = (targetWidth > 0 && currImgWidth >= targetWidth) ? targetWidth : currImgWidth;
        targetHeight = (targetHeight > 0 && currImgHeight >= targetHeight) ? targetHeight : currImgHeight;
    
        int type =
            (img.getTransparency() == Transparency.OPAQUE)
            ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = (BufferedImage) img;
        
        int w, h;
        if (higherQuality) {
            // Use multi-step technique: start with original size, then
            // scale down in multiple passes with drawImage()
            // until the target size is reached
            w = currImgWidth;
            h = currImgHeight;
        } else {
            // Use one-step technique: scale directly from original
            // size to target size with a single drawImage() call
            w = targetWidth;
            h = targetHeight;
        }

        do {
            if (higherQuality && w > targetWidth) {
                w /= 2;
                if (w < targetWidth) {
                    w = targetWidth;
                }
            }

            if (higherQuality && h > targetHeight) {
                h /= 2;
                if (h < targetHeight) {
                    h = targetHeight;
                }
            }

            BufferedImage tmp = new BufferedImage(w, h, type);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
            g2.drawImage(ret, 0, 0, w, h, null);
            g2.dispose();

            ret = tmp;
        } while (w != targetWidth || h != targetHeight);

        return ret;
    }

    public static void writeImg(
        BufferedImage bufferedImage,
        OutputStream outputStream,
        float quality,
        String imageType) throws IOException {
        
        Iterator<ImageWriter> iterator =
            ImageIO.getImageWritersByFormatName(imageType);
        ImageWriter imageWriter = iterator.next();
        ImageWriteParam imageWriteParam = imageWriter.getDefaultWriteParam();
        imageWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        imageWriteParam.setCompressionQuality(quality);
        ImageOutputStream imageOutputStream =
            new MemoryCacheImageOutputStream(outputStream);
        imageWriter.setOutput(imageOutputStream);
        IIOImage iioimage = new IIOImage(bufferedImage, null, null);
        imageWriter.write(null, iioimage, imageWriteParam);
        imageOutputStream.flush();
    }
}
