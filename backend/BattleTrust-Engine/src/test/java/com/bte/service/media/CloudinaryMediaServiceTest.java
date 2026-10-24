package com.bte.service.media;
import org.junit.jupiter.api.Test;import org.springframework.mock.web.MockMultipartFile;
import com.cloudinary.*;import com.bte.exception.*;import java.util.Map;
import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
class CloudinaryMediaServiceTest {
 @Test void validatesAndUploadsBytesWithServerChosenOptions()throws Exception{var cloud=mock(Cloudinary.class);var uploader=mock(Uploader.class);when(cloud.uploader()).thenReturn(uploader);when(uploader.upload(any(byte[].class),anyMap())).thenReturn(Map.of("secure_url","https://res.cloudinary.com/test/image/upload/sample.png"));var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);var service=new CloudinaryMediaService(cloud);assertTrue(service.uploadFile(new MockMultipartFile("file","sample.png","image/png",out.toByteArray()),"match-proofs").startsWith("https://"));verify(uploader).upload(any(byte[].class),argThat(m->m.get("folder").equals("battletrust/match-proofs")&&Boolean.FALSE.equals(m.get("overwrite"))));}
 @Test void rejectsNonImagesBeforeContactingCloud(){var cloud=mock(Cloudinary.class);var service=new CloudinaryMediaService(cloud);assertThrows(BadRequestException.class,()->service.uploadFile(new MockMultipartFile("file","fake.png","image/png",new byte[]{1,2,3}),"avatars"));verifyNoInteractions(cloud);}
}
