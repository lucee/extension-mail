package org.lucee.extension.mail.pool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class Pool {

	private final long maxIdle;
	private final int maxItems;
	private final ConcurrentHashMap<String, PoolItemWrap> map;
	private Controller controller;
	public long interval;

	public Pool(long maxIdle, int maxItems, long interval) {
		this.maxIdle = maxIdle;
		this.maxItems = maxItems;
		this.map = new ConcurrentHashMap<String, PoolItemWrap>();
		this.interval = interval;
	}

	public void put(String id, PoolItem value) throws Exception {
		PoolItemWrap item = new PoolItemWrap(value);
		PoolItemWrap previous = map.putIfAbsent(id, item);
		item.setLastAccess(System.currentTimeMillis());
		item.start();

		// we already have an item with that key, because we only have one we end the
		// existing one
		if (previous != null)
			previous.getValue().end();
		shrinkIfNecessary();
		startControllerIfNecessary();
	}

	public PoolItem get(String id) throws Exception {
		long now = System.currentTimeMillis();
		PoolItemWrap item = map.get(id);
		if (item == null)
			return null;
		if ((item.lastAccess() + maxIdle) < now || !item.getValue().isValid()) {
			item.end();
			stopControllerIfNecessary();
			return null;
		}
		return item.setLastAccess(now).getValue();
	}

	public boolean remove(String id) throws Exception {
		PoolItemWrap item = map.remove(id);
		if (item != null) {
			item.end();
			return true;
		}
		stopControllerIfNecessary();
		return false;
	}

	public boolean remove(PoolItem item) throws Exception {
		Iterator<Entry<String, PoolItemWrap>> it = map.entrySet().iterator();
		Entry<String, PoolItemWrap> e;
		while (it.hasNext()) {
			e = it.next();
			if (e.getValue().getValue() == item) {
				return remove(e.getKey());
			}
		}
		return false;
	}

	public void clean(boolean force) throws Exception {
		Iterator<Entry<String, PoolItemWrap>> it = map.entrySet().iterator();
		List<String> keysToRem = new ArrayList<>();

		Entry<String, PoolItemWrap> e;
		while (it.hasNext()) {
			e = it.next();
			long now = System.currentTimeMillis();
			try {
				if (force || ((e.getValue().lastAccess() + maxIdle) < now)) {
					e.getValue().end();
					keysToRem.add(e.getKey());
					// map.remove(e.getKey());
				}
			} catch (Exception ex) {
				keysToRem.add(e.getKey());
				// map.remove(e.getKey());
			}
		}

		if (keysToRem.size() > 0) {
			for (String k : keysToRem) {
				map.remove(k);
			}
		}

		stopControllerIfNecessary();
	}

	private void shrinkIfNecessary() {
		while (map.size() > maxItems) {
			removeOldest();
		}
	}

	private void removeOldest() {

		// get oldest
		Iterator<Entry<String, PoolItemWrap>> it = map.entrySet().iterator();
		Entry<String, PoolItemWrap> e;
		Entry<String, PoolItemWrap> oldest = null;
		while (it.hasNext()) {
			e = it.next();
			if (oldest == null || oldest.getValue().lastAccess() > e.getValue().lastAccess())
				oldest = e;
		}
		if (oldest != null)
			map.remove(oldest.getKey());
	}

	private void startControllerIfNecessary() {
		if (!map.isEmpty()) {
			if (controller == null || !controller.isAlive()) {
				controller = new Controller(this);
				controller.start();
			}
		}
	}

	private void stopControllerIfNecessary() {
		if (map.isEmpty()) {
			if (controller != null && controller.isAlive()) {
				controller.interrupt();
			}
		}
	}

	public final class Controller extends ParentThreasRefThread {

		private Pool pool;

		public Controller(Pool pool) {
			this.pool = pool;
		}

		@Override
		public void run() { // TODO handle exceptions
			while (true) {
				try {
					sleep(pool.interval);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (isInterrupted())
					break;
				try {
					pool.clean(false);
				} catch (Exception e) {
					addParentStacktrace(e);
					e.printStackTrace();
				}
				if (isInterrupted())
					break;

			}
		}
	}

	private static class ParentThreasRefThread extends Thread {
		private Thread thread;
		private StackTraceElement[] stes;

		public Thread getParentThread() {
			return thread;
		}

		@Override
		public synchronized void start() {
			this.stes = Thread.currentThread().getStackTrace();
			super.start();
		}

		public void addParentStacktrace(Throwable t) {
			StackTraceElement[] tmp = t.getStackTrace();
			List<StackTraceElement> merged = new ArrayList<>(tmp.length + stes.length - 1);

			for (StackTraceElement ste : tmp) {
				merged.add(ste);
			}
			{
				StackTraceElement ste;
				for (int i = 1; i < stes.length; i++) {
					ste = stes[i];
					merged.add(ste);
				}
			}
			t.setStackTrace(merged.toArray(new StackTraceElement[0]));
		}

	}

}
