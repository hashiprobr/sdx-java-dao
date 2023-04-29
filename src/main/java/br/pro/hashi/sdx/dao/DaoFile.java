package br.pro.hashi.sdx.dao;

import java.nio.channels.ReadableByteChannel;

import br.pro.hashi.sdx.dao.annotation.File;

/**
 * Represents the content of a {@link File} field.
 */
public final class DaoFile {
	private final ReadableByteChannel channel;
	private final String contentType;
	private final long contentLength;

	DaoFile(ReadableByteChannel channel, String contentType, long contentLength) {
		this.channel = channel;
		this.contentType = contentType;
		this.contentLength = contentLength;
	}

	/**
	 * Obtains a channel to read this file.
	 * 
	 * @return the channel
	 */
	public ReadableByteChannel getChannel() {
		return channel;
	}

	/**
	 * Obtains the content type of this file.
	 * 
	 * @return the type
	 */
	public String getContentType() {
		return contentType;
	}

	/**
	 * Obtains the content length of this file.
	 * 
	 * @return the length
	 */
	public long getContentLength() {
		return contentLength;
	}
}
