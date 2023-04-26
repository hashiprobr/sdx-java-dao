package br.pro.hashi.sdx.dao;

import java.nio.channels.ReadableByteChannel;

/**
 * Stub.
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
	 * Stub.
	 * 
	 * @return stub
	 */
	public ReadableByteChannel getChannel() {
		return channel;
	}

	/**
	 * Stub.
	 * 
	 * @return stub
	 */
	public String getContentType() {
		return contentType;
	}

	/**
	 * Stub.
	 * 
	 * @return stub
	 */
	public long getContentLength() {
		return contentLength;
	}
}
