package org.lucee.extension.mail;

import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * Serializable wrapper around {@link java.nio.charset.Charset}.
 *
 * <p>
 * {@code java.nio.charset.Charset} does not implement {@link Serializable}. Holding it directly as a
 * field in a serializable object - such as the {@link org.lucee.extension.mail.smtp.SMTPClient}
 * carried by a spooled mail task - therefore causes a {@code NotSerializableException} (e.g.
 * {@code java.io.NotSerializableException: sun.nio.cs.UTF_8}) when the object is persisted, which
 * silently drops the mail.
 * </p>
 *
 * <p>
 * This wrapper stores the charset <i>name</i> (a {@link String}, which is serializable) and lazily
 * resolves the {@link Charset} on demand, caching it in a transient field. The name is the single
 * source of truth, so instances round-trip cleanly through serialization.
 * </p>
 */
public final class CharsetSerializable implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String strCharset;
	private transient Charset charset;

	private CharsetSerializable(Charset charset, String strCharset) {
		this.charset = charset;
		this.strCharset = strCharset;
	}

	/**
	 * @param charset the charset to wrap
	 * @return a wrapper for the given charset, or {@code null} if {@code charset} is {@code null}
	 */
	public static CharsetSerializable of(Charset charset) {
		if (charset == null) return null;
		return new CharsetSerializable(charset, charset.name());
	}

	/**
	 * @param charsetName the charset name to wrap
	 * @return a wrapper for the given charset name, or {@code null} if {@code charsetName} is
	 *         {@code null}
	 */
	public static CharsetSerializable of(String charsetName) {
		if (charsetName == null) return null;
		return new CharsetSerializable(null, charsetName);
	}

	/**
	 * @return the wrapped charset, resolved from the stored name when necessary (e.g. after
	 *         deserialization, when the transient cache is empty)
	 */
	public Charset toCharset() {
		Charset c = charset;
		if (c == null && strCharset != null) {
			c = Charset.forName(strCharset);
			charset = c;
		}
		return c;
	}

	/**
	 * @return the charset name
	 */
	public String name() {
		return strCharset;
	}

	@Override
	public String toString() {
		return strCharset;
	}
}
