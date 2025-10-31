package org.lucee.extension.mail.smtp;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.net.mail.Server;

// FUTURE in future version this methods will be part of the Server interface, then this class is obsolete
public class ServerPro {

	private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
	private static final Object LOCK = new Object();

	private static volatile MethodHandle mhIsTLS;
	private static volatile MethodHandle mhIsSSL;
	private static volatile MethodHandle mhReuseConnections;
	private static volatile MethodHandle mhGetLifeTimeSpan;
	private static volatile MethodHandle mhGetIdleTimeSpan;
	private static volatile MethodHandle mhSetSSL;
	private static volatile MethodHandle mhSetTLS;
	private static volatile MethodHandle mhGetInstance;

	public static boolean isTLS(Server server) {
		if (mhIsTLS == null) {
			synchronized (LOCK) {
				if (mhIsTLS == null) {
					try {
						mhIsTLS = lookup.findVirtual(server.getClass(), "isTLS", MethodType.methodType(boolean.class));
					} catch (Exception e) {
						CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
						throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
					}
				}
			}
		}
		try {
			return (boolean) mhIsTLS.invoke(server);
		} catch (Throwable e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static boolean isSSL(Server server) {
		if (mhIsSSL == null) {
			synchronized (LOCK) {
				if (mhIsSSL == null) {
					try {
						mhIsSSL = lookup.findVirtual(server.getClass(), "isSSL", MethodType.methodType(boolean.class));
					} catch (Exception e) {
						CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
						throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
					}
				}
			}
		}
		try {
			return (boolean) mhIsSSL.invoke(server);
		} catch (Throwable e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static boolean reuseConnections(Server server) {
		if (mhReuseConnections == null) {
			synchronized (LOCK) {
				if (mhReuseConnections == null) {
					try {
						mhReuseConnections = lookup.findVirtual(server.getClass(), "reuseConnections",
								MethodType.methodType(boolean.class));
					} catch (Exception e) {
						CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
						throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
					}
				}
			}
		}
		try {
			return (boolean) mhReuseConnections.invoke(server);
		} catch (Throwable e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static long getLifeTimeSpan(Server server) {
		if (mhGetLifeTimeSpan == null) {
			synchronized (LOCK) {
				if (mhGetLifeTimeSpan == null) {
					try {
						mhGetLifeTimeSpan = lookup.findVirtual(server.getClass(), "getLifeTimeSpan",
								MethodType.methodType(long.class));
					} catch (Exception e) {
						CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
						throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
					}
				}
			}
		}
		try {
			return (long) mhGetLifeTimeSpan.invoke(server);
		} catch (Throwable e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static long getIdleTimeSpan(Server server) {
		if (mhGetIdleTimeSpan == null) {
			synchronized (LOCK) {
				if (mhGetIdleTimeSpan == null) {
					try {
						mhGetIdleTimeSpan = lookup.findVirtual(server.getClass(), "getIdleTimeSpan",
								MethodType.methodType(long.class));
					} catch (Exception e) {
						CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
						throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
					}
				}
			}
		}
		try {
			return (long) mhGetIdleTimeSpan.invoke(server);
		} catch (Throwable e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static void setSSL(Server server, boolean b) {
		if (mhSetSSL == null) {
			synchronized (LOCK) {
				if (mhSetSSL == null) {
					try {
						mhSetSSL = lookup.findVirtual(server.getClass(), "setSSL",
								MethodType.methodType(void.class, boolean.class));
					} catch (Exception e) {
						CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
						throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
					}
				}
			}
		}
		try {
			mhSetSSL.invoke(server, b);
		} catch (Throwable e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static void setTLS(Server server, boolean b) {
		if (mhSetTLS == null) {
			synchronized (LOCK) {
				if (mhSetTLS == null) {
					try {
						mhSetTLS = lookup.findVirtual(server.getClass(), "setTLS",
								MethodType.methodType(void.class, boolean.class));
					} catch (Exception e) {
						CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
						throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
					}
				}
			}
		}
		try {
			mhSetTLS.invoke(server, b);
		} catch (Throwable e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}

	public static Server getInstance(String server, int port, String usr, String pwd, long lifeTimespan,
			long idleTimespan, boolean defaultTls, boolean defaultSsl) {
		if (mhGetInstance == null) {
			synchronized (LOCK) {
				if (mhGetInstance == null) {
					try {
						Class<?> serverClass = CFMLEngineFactory.getInstance().getClassUtil()
								.loadClass("lucee.runtime.net.mail.ServerImpl");
						mhGetInstance = lookup.findStatic(serverClass, "getInstance",
								MethodType.methodType(serverClass, String.class, int.class, String.class, String.class,
										long.class, long.class, boolean.class, boolean.class));
					} catch (Exception e) {
						CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(e);
						throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
					}
				}
			}
		}
		try {
			return (Server) mhGetInstance.invoke(server, port, usr, pwd, lifeTimespan, idleTimespan, defaultTls,
					defaultSsl);
		} catch (Throwable e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageRuntimeException(e);
		}
	}
}