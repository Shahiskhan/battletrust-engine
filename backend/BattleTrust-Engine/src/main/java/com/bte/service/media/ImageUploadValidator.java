package com.bte.service.media;
import com.bte.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.Set;
public final class ImageUploadValidator {
    private ImageUploadValidator(){}
    public static byte[] validate(MultipartFile file,String folder) {
        if(file==null || file.isEmpty())throw new BadRequestException("An image file is required");
        if(file.getSize()>5*1024*1024)throw new BadRequestException("Image file must not exceed 5 MB");
        if(folder==null || !folder.matches("[a-zA-Z0-9_-]{1,64}"))throw new BadRequestException("Invalid media folder");
        String type=file.getContentType();
        if(type==null || !Set.of("image/jpeg","image/png","image/gif").contains(type.toLowerCase(java.util.Locale.ROOT)))throw new BadRequestException("Only JPEG, PNG and GIF images are supported");
        try {
            byte[] bytes=file.getBytes();
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if(stream==null)throw new BadRequestException("Invalid image");
                var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new BadRequestException("Uploaded file is not a valid image");
                var reader=readers.next();
                try {reader.setInput(stream,true,true);String format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);String expected=type.substring(6).toLowerCase(java.util.Locale.ROOT);
                    if(!format.equals(expected) || reader.getWidth(0)<1 || reader.getHeight(0)<1 || (long)reader.getWidth(0)*reader.getHeight(0)>40_000_000)throw new BadRequestException("Invalid image content or dimensions");
                    reader.read(0); // Decode the image, rather than trusting MIME headers or a file signature.
                }finally{reader.dispose();}
            }
            return bytes;
        }catch(IOException e){throw new BadRequestException("Image could not be decoded");}
    }
}
