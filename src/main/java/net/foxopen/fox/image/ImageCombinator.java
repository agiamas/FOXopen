package net.foxopen.fox.image;

import net.foxopen.fox.ComponentImage;
import net.foxopen.fox.ex.ExInternal;
import net.foxopen.fox.track.Track;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Joins together multiple images, for example, transparent GIF files. Images
 * can be built up by instantiating with a base layer (Image or BufferedImage)
 * and then adding layers on top of it one by one. At any stage, the caller can
 * use getBufferedImage to retrieve the current combined image.
 * Supports PNG and GIF with transparency - other formats may work - untested.
 *
 * NOTE: Java 1.4 does not support writing GIF files natively due to patent
 * and licensing issues. This has been resolved in Java 1.6. PNG should be used
 * as a workaround for this.
 */
public class ImageCombinator {

  private final BufferedImage mBufferedImage;
  private final String mContentType;

  /**
   * Combines the given images, with the 0th image in the list being the bottom layer. The images must have the same dimensions
   * and image type. The returned object is in a state where the final image can be read using one of the byte getter methods.
   * @param pImageList List of at least 2 images of the same dimensions.
   * @return ImageCombinator containing all the given images combined.
   */
  public static ImageCombinator createAndCombine(List<ComponentImage> pImageList) {

    if(pImageList.size() < 2) {
      throw new ExInternal("Image list must contain at least 2 images");
    }

    Track.pushInfo("CombinatorCreate");
    ImageCombinator lCombinator;
    try {
      lCombinator = new ImageCombinator(pImageList.get(0));
    }
    finally {
      Track.pop("CombinatorCreate");
    }

    for(int i=1; i<pImageList.size(); i++) {
      ComponentImage lImage = pImageList.get(i);
      Track.pushInfo("CombinatorAddLayer", "Image " + lImage.getName());
      try {
        lCombinator.addLayer(lImage);
      }
      finally {
        Track.pop("CombinatorAddLayer");
      }
    }

    return lCombinator;
  }

  /**
   * Constructs a new ImageCombinator for joining together images.
   * @param pFoxComponent ComponentImage to convert to BufferedImage and use as base layer
   */
  private ImageCombinator (ComponentImage pFoxComponent) {
    if (pFoxComponent != null) {
      mContentType = pFoxComponent.getType();
      mBufferedImage = createBufferedImage(pFoxComponent);
    }
    else {
      throw new ExInternal("Null ComponentImage passed to ImageCombinator constructor");
    }
  }

  /**
   * Adds an image layer to the existing image.
   * @param pFoxComponent ComponentImage to add as layer
   * @throws ExInternal
   */
  private void addLayer (ComponentImage pFoxComponent)
  throws ExInternal {
    if (pFoxComponent != null) {
      BufferedImage lTemp = createBufferedImage(pFoxComponent);
      // Belt and braces check. Not a necessity (larger images will scale down appropriately), but further
      // metadata would be required to correctly position smaller images on a larger canvas
      if (lTemp.getWidth() == mBufferedImage.getWidth() && lTemp.getHeight() == mBufferedImage.getHeight()) {
        drawImageToBuffer(mBufferedImage, lTemp);
      }
      else {
        // Images could have many layers, so provide a reasonable error message to speed up resolution of size mismatching
        throw new ExInternal("Image dimensions must match: base image is " +  mBufferedImage.getWidth() + "x" + mBufferedImage.getHeight()
          + ", new layer image ('" + pFoxComponent.getName() + "') is " + lTemp.getWidth() + "x" + lTemp.getHeight());
      }
    }
    else {
      throw new ExInternal("Null ComponentImage passed to ImageCombinator.addLayer()");
    }
  }

  /**
   * Gets the byte array from the ComponentImage passed and converts to a BufferedImage.
   * @param pFoxComponent ComponentImage to convert to BufferedImage
   * @return new BufferedImage instance
   * @throws ExInternal
   */
  private BufferedImage createBufferedImage (ComponentImage pFoxComponent) {
    // Check that added layers match the type
    if (!mContentType.equals(pFoxComponent.getType())) {
      throw new ExInternal("Image types must match: base image is " + mContentType + ", new layer image ("
        + pFoxComponent.getName() + ") is " + pFoxComponent.getType());
    }

    try {
      // Get base image
      BufferedImage lTempComponent = ImageIO.read(new ByteArrayInputStream(pFoxComponent.getByteArray()));

      // Create temporary buffer as image with alpha transparency
      BufferedImage lTempBuffer = new BufferedImage(lTempComponent.getWidth(), lTempComponent.getHeight(), BufferedImage.TYPE_INT_ARGB);

      // Draw image onto transparency (ensures that all transparent layers in the future
      // are drawn correctly - fixes buggy appearance when using non-transparent base image
      drawImageToBuffer(lTempBuffer, lTempComponent);
      return lTempBuffer;
    }
    catch (IOException ex) {
      throw new ExInternal("Couldn't read image in ImageCombinator.createBufferedImage", ex);
    }
  }

  /**
   * Draws an Image on top of a BufferedImage.
   * @param pBufferedImage the BufferedImage to use as the base layer
   * @param pImageToDraw the Image to draw over the top
   * @throws ExInternal
   */
  private static void drawImageToBuffer (BufferedImage pBufferedImage, Image pImageToDraw) {
    Graphics lGraphics = pBufferedImage.createGraphics();
    lGraphics.drawImage(pImageToDraw, 0, 0, null);
    lGraphics.dispose();
  }

  /**
   * Get the current BufferedImage as a byte array.
   * @return byte array
   * @throws ExInternal
   */
  public byte[] getOutputByteArray() {
    return getByteArrayOutputStream().toByteArray();
  }

  /**
   * Get the current BufferedImage via an output stream.
   * @return byte array stream
   * @throws ExInternal
   */
  public ByteArrayOutputStream getByteArrayOutputStream()
  throws ExInternal {
    ByteArrayOutputStream lByteArrayOutputStream = new ByteArrayOutputStream();
    try {
      ImageIO.write(mBufferedImage, mContentType.replace("image/", ""), lByteArrayOutputStream);
    }
    catch (IOException ex) {
      throw new ExInternal("Could not write BufferedImage to byte array output stream in ImageCombinator.getOutputByteArray()", ex);
    }
    return lByteArrayOutputStream;
  }

  /**
   * Gets the height of the current base image buffer.
   * @return height
   */
  public int getHeight () {
    return mBufferedImage.getHeight();
  }

  /**
   * Gets the width of the current base image buffer.
   * @return width
   */
  public int getWidth () {
    return mBufferedImage.getWidth();
  }

  /**
   * Gets the content type of the base image.
   * @return content type as String
   */
  public String getContentType () {
    return mContentType;
  }

}
