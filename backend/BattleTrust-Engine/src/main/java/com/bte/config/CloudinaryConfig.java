package com.bte.config;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
@Configuration @ConditionalOnProperty(name="app.media.provider",havingValue="cloudinary")
public class CloudinaryConfig {
    @Bean public Cloudinary cloudinary(@Value("${app.cloudinary.cloud-name:}")String name,@Value("${app.cloudinary.api-key:}")String key,@Value("${app.cloudinary.api-secret:}")String secret) {
        if(java.util.stream.Stream.of(name,key,secret).anyMatch(v -> v.isBlank() || v.startsWith("your_")))throw new IllegalStateException("Cloudinary requires real CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET values");
        return new Cloudinary(ObjectUtils.asMap("cloud_name",name,"api_key",key,"api_secret",secret,"secure",true));
    }
}
