package org.lucee.extension.mail.smtp;

// Imports
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;

import lucee.commons.io.res.Resource;
import lucee.loader.engine.CFMLEngineFactory;

/**
 * File Data Source.
 */
public final class ResourceDataSource implements DataSource {

	/**
	 * File source.
	 */
	private final Resource _file;

	/**
	 * Constructor of the class
	 * 
	 * @param res
	 *            source
	 */
	public ResourceDataSource(Resource res) {
		_file = res;
	}

	/**
	 * Get name.
	 * 
	 * @return Name of resource
	 */
	@Override
	public String getName() {
		return _file.getName();
	}

	/**
	 * Get Resource.
	 * 
	 * @return Resource
	 */
	public Resource getResource() {
		return _file;
	}

	/**
	 * Get input stream.
	 * 
	 * @return Input stream
	 * @throws IOException
	 *             IO exception occurred
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return CFMLEngineFactory.getInstance().getIOUtil().toBufferedInputStream(_file.getInputStream());
	}

	/**
	 * Get content type.
	 * 
	 * @return Content type
	 */
	@Override
	public String getContentType() {
		return CFMLEngineFactory.getInstance().getResourceUtil().getContentType(_file).getMimeType();
	}

	/**
	 * Get output stream.
	 * 
	 * @return Output stream
	 * @throws IOException
	 *             IO exception occurred
	 */
	@Override
	public OutputStream getOutputStream() throws IOException {
		if (!_file.isWriteable()) {
			throw new IOException("Cannot write");
		}
		return CFMLEngineFactory.getInstance().getIOUtil().toBufferedOutputStream(_file.getOutputStream());
	}
}