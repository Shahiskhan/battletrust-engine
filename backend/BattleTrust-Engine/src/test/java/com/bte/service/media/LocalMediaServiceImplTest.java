package com.bte.service.media;

import com.bte.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMediaServiceImplTest {

	@TempDir
	Path storageDirectory;

	@Test
	void savesVerifiedImageWithinConfiguredDirectory() throws IOException {
		LocalMediaServiceImpl mediaService =
				new LocalMediaServiceImpl(storageDirectory.toString(), "/media");
		MockMultipartFile file = new MockMultipartFile(
				"file", "logo.png", "image/png", createPng());

		String url = mediaService.uploadFile(file, "tournament-logos");

		assertTrue(url.matches("/media/tournament-logos/[0-9a-f-]{36}\\.png"));
		assertTrue(Files.exists(storageDirectory.resolve("tournament-logos")
				.resolve(url.substring(url.lastIndexOf('/') + 1))));
	}

	@Test
	void rejectsInvalidImageAndUnsafeFolder() {
		LocalMediaServiceImpl mediaService =
				new LocalMediaServiceImpl(storageDirectory.toString(), "/media");
		MockMultipartFile invalidImage = new MockMultipartFile(
				"file", "not-image.png", "image/png", "not an image".getBytes());
		MockMultipartFile validShapeButWrongContent = new MockMultipartFile(
				"file", "logo.png", "image/png", new byte[] {
						(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
				});

		assertThrows(BadRequestException.class, () -> mediaService.uploadFile(invalidImage, "logos"));
		assertThrows(BadRequestException.class, () -> mediaService.uploadFile(validShapeButWrongContent, "../outside"));
	}

	private byte[] createPng() throws IOException {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}
}
