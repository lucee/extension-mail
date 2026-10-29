package org.lucee.extension.mail;

import java.util.ArrayList;
import java.util.List;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Array;
import lucee.runtime.type.Query;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;
import lucee.runtime.util.Creation;

/**
 * Handling of the attribute "returntype" (and "columnKey") for the tag actions that return a query,
 * same behaviour as cfquery in Lucee core. The default is a query.
 */
public final class QueryReturnType {

	public static final int QUERY = 1;
	public static final int ARRAY = 2;
	public static final int STRUCT = 3;

	private QueryReturnType() {
	}

	/**
	 * @return one of QUERY,ARRAY,STRUCT or -1 if the value is not valid; an empty value is QUERY
	 */
	public static int parse(String strReturntype) {
		if (strReturntype == null || strReturntype.trim().isEmpty()) return QUERY;
		strReturntype = strReturntype.toLowerCase().trim();

		if (strReturntype.equals("query")) return QUERY;
		if (strReturntype.equals("struct")) return STRUCT;
		if (strReturntype.equals("array") || strReturntype.equals("array_of_struct") || strReturntype.equals("array-of-struct") || strReturntype.equals("arrayofstruct")) {
			return ARRAY;
		}
		return -1;
	}

	public static int parse(String strReturntype, String tagName) throws PageException {
		int rt = parse(strReturntype);
		if (rt == -1) {
			throw CFMLEngineFactory.getInstance().getExceptionUtil().createApplicationException("Attribute [returntype] of tag [" + tagName + "] has an invalid value",
					"valid values are [query,array,struct] but value was [" + strReturntype.trim() + "]");
		}
		return rt;
	}

	/**
	 * converts a query to the requested return type
	 * 
	 * @param qry the query
	 * @param returnType one of QUERY,ARRAY,STRUCT
	 * @param columnKey only used for STRUCT, the name of the column (or a comma separated list of columns, creating one struct level per column) the value of is used as key
	 * @return the query itself for QUERY, otherwise an array of structs or a struct of structs
	 */
	public static Object convert(Query qry, int returnType, String columnKey) throws PageException {
		if (returnType != ARRAY && returnType != STRUCT) return qry;

		CFMLEngine factory = CFMLEngineFactory.getInstance();
		Creation creator = factory.getCreationUtil();
		Cast caster = factory.getCastUtil();
		Key[] columns = qry.getColumnNames();
		int rows = qry.getRecordcount();

		if (returnType == ARRAY) {
			Array arr = creator.createArray();
			for (int r = 1; r <= rows; r++) {
				arr.appendEL(toStruct(creator, qry, columns, r));
			}
			return arr;
		}

		Struct sct = creator.createStruct(Struct.TYPE_LINKED);

		// the column key can be a comma separated list of columns
		List<Key> keys = new ArrayList<Key>();
		if (columnKey != null) {
			Key ck;
			for (String name: columnKey.split(",")) {
				if (name.trim().isEmpty()) continue;
				ck = caster.toKey(name.trim());
				if (qry.getColumn(ck, null) == null) {
					throw factory.getExceptionUtil().createApplicationException("Attribute [columnKey] has an invalid value",
							"the query has no column with the name [" + name.trim() + "]");
				}
				keys.add(ck);
			}
		}

		// no column key, the query must have a single record, the struct is that record (same as cfquery)
		if (keys.isEmpty()) {
			if (rows == 0) return sct;
			if (rows > 1) throw factory.getExceptionUtil()
					.createApplicationException("Attribute [columnKey] is required when return type is set to struct and more than one record is returned");
			return toStruct(creator, qry, columns, 1);
		}

		// one struct level per column, if a key combination exists more than once the last row wins
		Struct current, sub;
		Object k, tmp;
		Key key;
		int last = keys.size() - 1;
		for (int r = 1; r <= rows; r++) {
			current = sct;
			for (int i = 0; i <= last; i++) {
				k = qry.getAt(keys.get(i), r, null);
				key = k == null ? creator.createKey("") : caster.toKey(k);
				if (i == last) {
					current.setEL(key, toStruct(creator, qry, columns, r));
				}
				else {
					tmp = current.get(key, null);
					if (tmp instanceof Struct) sub = (Struct) tmp;
					else {
						sub = creator.createStruct(Struct.TYPE_LINKED);
						current.setEL(key, sub);
					}
					current = sub;
				}
			}
		}
		return sct;
	}

	private static Struct toStruct(Creation creator, Query qry, Key[] columns, int row) throws PageException {
		Struct row_ = creator.createStruct(Struct.TYPE_LINKED);
		for (Key c: columns) {
			row_.setEL(c, qry.getAt(c, row, null));
		}
		return row_;
	}
}
