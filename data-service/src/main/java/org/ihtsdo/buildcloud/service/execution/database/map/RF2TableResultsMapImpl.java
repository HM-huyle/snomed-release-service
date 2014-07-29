package org.ihtsdo.buildcloud.service.execution.database.map;

import org.ihtsdo.buildcloud.service.execution.RF2Constants;
import org.ihtsdo.buildcloud.service.execution.database.RF2TableResults;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class RF2TableResultsMapImpl implements RF2TableResults {

	public static final String FORMAT = "%s" + RF2Constants.COLUMN_SEPARATOR + "%s" + RF2Constants.COLUMN_SEPARATOR + "%s";
	private final Map<Key, String> table;
	private Set<Key> keys;
	private Iterator<Key> iterator;

	public RF2TableResultsMapImpl(Map<Key, String> table) {
		this.table = table;
		keys = table.keySet();
		iterator = keys.iterator();
	}

	@Override
	public String nextLine() throws SQLException {
		if (iterator.hasNext()) {
			Key key = iterator.next();
			return String.format(FORMAT, key.getUuidString(), key.getDate(), table.get(key));
		} else {
			return null;
		}
	}

}