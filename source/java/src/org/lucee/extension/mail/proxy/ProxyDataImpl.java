package org.lucee.extension.mail.proxy;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.net.proxy.ProxyData;
import lucee.runtime.type.Array;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;

public final class ProxyDataImpl implements ProxyData, Serializable {

	public static final ProxyData NO_PROXY = new ProxyDataImpl();

	public static final Set<String> LOCALS = new HashSet<String>();
	static {
		LOCALS.add("localhost");
		LOCALS.add("127.0.0.1");
		LOCALS.add("0:0:0:0:0:0:0:1");
	}

	private String server;
	private int port = -1;
	private String username;
	private String password;

	private Set<String> excludes;
	private Set<String> includes;

	private boolean includeLocals;

	public ProxyDataImpl(String server, int port, String username, String password) {
		if (!Util.isEmpty(server, true))
			this.server = server;
		if (port > 0)
			this.port = port;
		if (!Util.isEmpty(username, true))
			this.username = username;
		if (!Util.isEmpty(password, true))
			this.password = password;
	}

	public ProxyDataImpl() {
	}

	@Override
	public void release() {
		server = null;
		port = -1;
		username = null;
		password = null;
	}

	/**
	 * @return the password
	 */
	@Override
	public String getPassword() {
		return password;
	}

	/**
	 * @param password
	 *            the password to set
	 */
	@Override
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * @return the port
	 */
	@Override
	public int getPort() {
		return port;
	}

	/**
	 * @param port
	 *            the port to set
	 */
	@Override
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * @return the server
	 */
	@Override
	public String getServer() {
		return server;
	}

	/**
	 * @param server
	 *            the server to set
	 */
	@Override
	public void setServer(String server) {
		this.server = server;
	}

	/**
	 * @return the username
	 */
	@Override
	public String getUsername() {
		return username;
	}

	/**
	 * @param username
	 *            the username to set
	 */
	@Override
	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof ProxyData))
			return false;

		ProxyData other = (ProxyData) obj;

		return _eq(other.getServer(), server) && _eq(other.getUsername(), username)
				&& _eq(other.getPassword(), password) && other.getPort() == port;

	}

	private boolean _eq(String left, String right) {
		if (left == null)
			return right == null;
		return left.equals(right);
	}

	public static boolean isValid(ProxyData pd) {
		if (pd == null || pd.equals(NO_PROXY) || Util.isEmpty(pd.getServer(), true))
			return false;
		return true;
	}

	/**
	 * check if the proxy is valid for the given host
	 */
	public static boolean isValid(ProxyData pd, String host) {
		if (pd == null || pd.equals(NO_PROXY) || Util.isEmpty(pd.getServer(), true))
			return false;
		return isProxyEnableFor(pd, host);
	}

	/**
	 * returns the given proxy in case it is valid for the given host, if not null
	 * is returned
	 */
	public static ProxyData validate(ProxyData pd, String host) {
		if (isValid(pd, host))
			return pd;
		return null;
	}

	public static boolean isProxyEnableFor(ProxyData pd, String host) {
		if (pd == null)
			return false;
		ProxyDataImpl pdi = (ProxyDataImpl) pd;

		if (Util.isEmpty(host))
			return true;
		host = host.trim().toLowerCase();

		boolean doesInclude = false;

		// if we have includes it needs to be part of it
		if (pdi.includes != null && !pdi.includes.isEmpty()) {
			if (!pdi.includes.contains(host))
				return false;
			doesInclude = true;
		}

		if (!doesInclude && LOCALS.contains(host))
			return false;

		// if we have excludes it should NOT be part of it
		if (pdi.excludes != null && !pdi.excludes.isEmpty()) {
			if (pdi.excludes.contains(host))
				return false;
		}
		return true;
	}

	public static boolean hasCredentials(ProxyData data) {
		return Util.isEmpty(data.getUsername(), true);
	}

	public static ProxyData getInstance(String proxyserver, int proxyport, String proxyuser, String proxypassword) {
		if (Util.isEmpty(proxyserver, true))
			return null;
		return new ProxyDataImpl(proxyserver, proxyport, proxyuser, proxypassword);
	}

	@Override
	public String toString() {
		return "server:" + server + ";port:" + port + ";user:" + username + ";pass:" + password;
	}

	public void setExcludes(Set<String> excludes) {
		this.excludes = excludes;
	}

	public Set<String> getExcludes() {
		return excludes;
	}

	public void setIncludes(Set<String> includes) {
		this.includes = includes;
	}

	public Set<String> getIncludes() {
		return includes;
	}

	public static ProxyData toProxyData(Struct sct, ProxyData defaultValue) {
		ProxyDataImpl pd = null;
		if (sct != null) {
			Cast caster = CFMLEngineFactory.getInstance().getCastUtil();

			String srv = caster.toString(sct.get("server", null), null);
			Integer port = caster.toInteger(sct.get("port", null), null);
			String usr = caster.toString(sct.get("username", null), null);
			String pwd = caster.toString(sct.get("password", null), null);

			if (!Util.isEmpty(srv, true)) {
				pd = new ProxyDataImpl();
				pd.setServer(srv.trim());
				if (port != null)
					pd.setPort(port);
				if (!Util.isEmpty(usr, true)) {
					pd.setUsername(usr);
					pd.setPassword(pwd == null ? "" : pwd);
				}

				// includes/excludes
				pd.setExcludes(toStringSet(sct.get("excludes", null)));
				pd.setIncludes(toStringSet(sct.get("includes", null)));
			}
		}
		if (pd == null)
			return defaultValue;
		return pd;
	}

	/*
	 * public static String[] toStringArray(Object obj) { String[] rtn = null; if
	 * (Decision.isArray(obj)) { Array arr = Caster.toArray(obj, null); if (arr !=
	 * null) { rtn = ListUtil.trim(ListUtil.trimItems(ListUtil.toStringArray(arr,
	 * null))); }
	 * 
	 * } else { String list = Caster.toString(obj, null); if
	 * (!StringUtil.isEmpty(list, true)) { rtn =
	 * ListUtil.trim(ListUtil.trimItems(ListUtil.listToStringArray(list, ","))); } }
	 * return rtn; }
	 */
	public static Set<String> toStringSet(Object obj) {
		Set<String> rtn = null;
		CFMLEngine eng = CFMLEngineFactory.getInstance();
		Array arr = null;
		if (eng.getDecisionUtil().isArray(obj)) {
			arr = eng.getCastUtil().toArray(obj, null);
		} else {
			String list = eng.getCastUtil().toString(obj, null);
			if (!Util.isEmpty(list, true)) {
				arr = eng.getListUtil().toArray(list, ",");
			}
		}

		if (arr != null) {
			rtn = new HashSet<String>();
			Iterator<?> it = arr.getIterator();
			String str;
			while (it.hasNext()) {
				str = eng.getCastUtil().toString(it.next(), null);
				if (!Util.isEmpty(str, true))
					rtn.add(str.trim().toLowerCase());
			}
			if (rtn.isEmpty())
				rtn = null;
		}

		return rtn;
	}

}