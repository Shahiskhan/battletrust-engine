package com.bte.service.media;

import org.springframework.web.multipart.MultipartFile;

public interface MediaService {

	String uploadFile(MultipartFile file, String folderName);
}
