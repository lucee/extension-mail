package org.lucee.extension.mail.pool;

public interface PoolItem {
	public void start() throws Exception;

	public boolean isValid();

	public void end() throws Exception;

	/**
	 * @return true while an action is working with this item, the pool must not end it then (LDEV-4220)
	 */
	public default boolean isInUse() {
		return false;
	}

	/**
	 * @return time (millis) this item was last released after use, 0 if unknown
	 */
	public default long lastUsed() {
		return 0;
	}
}
