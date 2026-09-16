package com.cloudticket.order;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.MultiFormatWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/** Renders a QR code PNG as a data URL. Used only by the simulated payment flow. */
@Component
public class QrCodeGenerator {
  static { System.setProperty("java.awt.headless", "true"); }

  public String toDataUrl(String content) {
    try {
      BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 280, 280);
      BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
      for (int x = 0; x < matrix.getWidth(); x++) {
        for (int y = 0; y < matrix.getHeight(); y++) {
          image.setRGB(x, y, matrix.get(x, y) ? 0xFF111927 : 0xFFFFFFFF);
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "PNG", out);
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    } catch (Exception failure) {
      throw new IllegalStateException("unable to generate qr code", failure);
    }
  }
}
