package me.jsjlog.blog.support;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** 작은 실제 이미지와 픽셀을 할당하지 않는 경계 검사 파일. */
public final class ImageTestFiles {
	private ImageTestFiles() { }

	public static byte[] image(String format, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height,
				format.equals("webp-alpha") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(format.equals("webp-alpha") ? new Color(255, 128, 0, 100) : Color.ORANGE);
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		if (format.startsWith("webp")) {
			return ImmutableImage.wrapAwt(image).bytes(format.equals("webp-lossless")
					? WebpWriter.DEFAULT.withLossless() : WebpWriter.DEFAULT);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(image, format, out)) {
			throw new IOException("Unsupported fixture: " + format);
		}
		return out.toByteArray();
	}

	public static byte[] pngHeader(int width, int height) {
		ByteBuffer out = ByteBuffer.allocate(33);
		out.putLong(0x89504e470d0a1a0aL).putInt(13).putInt(0x49484452);
		out.putInt(width).putInt(height).put(new byte[]{8, 2, 0, 0, 0});
		CRC32 crc = new CRC32();
		crc.update(out.array(), 12, 17);
		out.putInt((int) crc.getValue());
		return out.array();
	}

	public static byte[] jpegWithDeclaredDimensions(int width, int height) throws IOException {
		byte[] bytes = image("jpeg", 32, 24);
		for (int offset = 2; offset < bytes.length - 8;) {
			int marker = bytes[offset + 1] & 0xff;
			if (marker == 0xc0 || marker == 0xc2) {
				ByteBuffer.wrap(bytes).putShort(offset + 5, (short) height).putShort(offset + 7, (short) width);
				return bytes;
			}
			offset += 2 + ((bytes[offset + 2] & 0xff) << 8) + (bytes[offset + 3] & 0xff);
		}
		throw new IOException("JPEG fixture has no frame header");
	}

	public static byte[] webp(byte[]... chunks) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write("WEBP".getBytes(StandardCharsets.US_ASCII));
		for (byte[] chunk : chunks) {
			body.write(chunk);
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
		out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size()).array());
		out.write(body.toByteArray());
		return out.toByteArray();
	}

	public static byte[] chunk(String type, byte[] payload) {
		return ByteBuffer.allocate(8 + payload.length + (payload.length & 1)).order(ByteOrder.LITTLE_ENDIAN)
				.put(type.getBytes(StandardCharsets.US_ASCII)).putInt(payload.length).put(payload).array();
	}

	public static byte[] rotatedJpeg() throws IOException {
		byte[] jpeg = image("jpeg", 80, 40);
		// EXIF Orientation=6 (시계 방향 90도). 원본 바이트 보존 여부도 확인한다.
		byte[] exif = { 'E','x','i','f',0,0, 'M','M',0,42,0,0,0,8,
				0,1, 1,18,0,3,0,0,0,1,0,6,0,0, 0,0,0,0 };
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2);
		out.write(new byte[]{(byte) 0xff, (byte) 0xe1, 0, (byte) (exif.length + 2)});
		out.write(exif);
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}
}
