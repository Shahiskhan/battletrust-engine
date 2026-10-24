package com.bte.service.media;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.bte.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.multipart.MultipartFile;
@Service @RequiredArgsConstructor @ConditionalOnProperty(name="app.media.provider",havingValue="cloudinary")
public class CloudinaryMediaService implements MediaService {
    private final Cloudinary cloudinary;
    public String uploadFile(MultipartFile file,String folder) {
        byte[] bytes=ImageUploadValidator.validate(file,folder);
        try {
            var result=cloudinary.uploader().upload(bytes,ObjectUtils.asMap("folder","battletrust/"+folder,"resource_type","image","public_id",java.util.UUID.randomUUID().toString(),"overwrite",false));
            if(!(result.get("secure_url") instanceof String url) || !url.startsWith("https://"))throw new ServiceUnavailableException("Image storage returned an invalid response");
            return url;
        }catch(java.io.IOException e){throw new ServiceUnavailableException("Image storage is temporarily unavailable");}
    }
}
