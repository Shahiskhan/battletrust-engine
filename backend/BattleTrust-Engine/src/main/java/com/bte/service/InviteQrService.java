package com.bte.service;
import org.springframework.stereotype.Service;
@Service
public class InviteQrService {
 public String image(String value){try{var matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(value,com.google.zxing.BarcodeFormat.QR_CODE,300,300);var image=new java.awt.image.BufferedImage(300,300,java.awt.image.BufferedImage.TYPE_INT_RGB);for(int y=0;y<300;y++)for(int x=0;x<300;x++)image.setRGB(x,y,matrix.get(x,y)?0x000000:0xffffff);var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",output);return "data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(output.toByteArray());}catch(Exception e){throw new IllegalStateException("Could not generate invite QR",e);}}
}
