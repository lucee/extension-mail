package org.lucee.extension.mail;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.Date;

import org.lucee.extension.mail.spooler.MailSpoolerTask;

import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.net.proxy.ProxyData;
import lucee.runtime.spooler.SpoolerEngine;
import lucee.runtime.spooler.SpoolerTask;

public class ReflectionUtil {

	private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[] {};
	private static final Object[] EMPTY_OBJECT_ARRAY = new Object[] {};
	private static final Class<?>[] ADD = new Class<?>[] { ConfigWeb.class, SpoolerTask.class };

	private static final Class<?>[] LOG = new Class<?>[] { ConfigWeb.class, String.class, String.class, long.class,
			Object.class };
	private static final Class<?>[] START = new Class<?>[] { ProxyData.class };
	private static final Class<?>[] TO_OFFSET_IF_NEEDED = new Class<?>[] { ZoneId.class, Date.class };

	private static Method isMailSendPartial;
	private static Method isUserset;
	private static Method add;
	private static Method getActionMonitorCollector;
	private static Method log;
	private static Method start;
	private static Class<?> proxyClass;
	private static Method end;
	private static Class<?> dateTimeUtilClass;
	private static Method toOffsetIfNeeded;

	public static boolean isMailSendPartial(Config config) {
		try {
			if (isMailSendPartial == null) {
				isMailSendPartial = config.getClass().getMethod("isMailSendPartial", EMPTY_CLASS_ARRAY);
			}
			return CFMLEngineFactory.getInstance().getCastUtil()
					.toBooleanValue(isMailSendPartial.invoke(config, EMPTY_OBJECT_ARRAY));
		} catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static boolean isUserset(Config config) {
		try {
			if (isUserset == null) {
				isUserset = config.getClass().getMethod("isUserset", EMPTY_CLASS_ARRAY);
			}
			return CFMLEngineFactory.getInstance().getCastUtil()
					.toBooleanValue(isUserset.invoke(config, EMPTY_OBJECT_ARRAY));
		} catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static Object getActionMonitorCollector(ConfigWeb config) {
		try {
			if (getActionMonitorCollector == null) {
				getActionMonitorCollector = config.getClass().getMethod("getActionMonitorCollector", EMPTY_CLASS_ARRAY);
			}
			return getActionMonitorCollector.invoke(config, EMPTY_OBJECT_ARRAY);
		} catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static void add(SpoolerEngine spoolerEngine, ConfigWeb config, MailSpoolerTask mst) {
		try {
			if (add == null) {
				add = spoolerEngine.getClass().getMethod("add", ADD);

			}
			add.invoke(spoolerEngine, new Object[] { config, mst });
		} catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}

	}

	public static void log(Object amc, ConfigWeb config, String type, String label, long executionTime, Object data) {
		try {
			if (log == null) {
				log = amc.getClass().getMethod("log", LOG);
			}
			log.invoke(amc, new Object[] { config, type, label, executionTime, data });
		} catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static void Proxy_start(ProxyData proxyData) {
		try {
			if (start == null) {
				start = getProxyClass().getMethod("start", START);
			}
			start.invoke(null, new Object[] { proxyData });
		} catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static void Proxy_end() {
		try {
			if (end == null) {
				end = getProxyClass().getMethod("end", EMPTY_CLASS_ARRAY);
			}
			end.invoke(null, EMPTY_OBJECT_ARRAY);
		} catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	private static Class<?> getProxyClass() throws IOException {
		if (proxyClass == null) {
			proxyClass = CFMLEngineFactory.getInstance().getClassUtil().loadClass("lucee.runtime.net.proxy.Proxy");
		}
		return proxyClass;
	}

	public static ZoneId DateTimeUtil_toOffsetIfNeeded(ZoneId zone, Date date) {
		try {
			if (toOffsetIfNeeded == null) {
				toOffsetIfNeeded = getDateTimeUtilClass().getMethod("toOffsetIfNeeded", TO_OFFSET_IF_NEEDED);
			}
			return (ZoneId) toOffsetIfNeeded.invoke(null, new Object[] { zone, date });
		} catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	private static Class<?> getDateTimeUtilClass() throws IOException {
		if (dateTimeUtilClass == null) {
			dateTimeUtilClass = CFMLEngineFactory.getInstance().getClassUtil()
					.loadClass("lucee.commons.date.DateTimeUtil");
		}
		return dateTimeUtilClass;
	}

}
