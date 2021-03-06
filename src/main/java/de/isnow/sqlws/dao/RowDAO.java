/*
 * Copyright (c) 2003, Orientation in Objects GmbH, www.oio.de
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation 
 *   and/or other materials provided with the distribution.
 * - Neither the name of Orienation in Objects GmbH nor the names of its 
 *   contributors may be used to endorse or promote products derived from this 
 *   software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * ${file_name} Created on ${date} by ${user}
 *  
 * ${todo}
 * 
 */

package de.isnow.sqlws.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import de.isnow.sqlws.model.Column;
import de.isnow.sqlws.model.DBConnection;
import de.isnow.sqlws.model.Database;
import lombok.extern.slf4j.Slf4j;

/**
 * @author tbayer
 *
 */
@Slf4j
public class RowDAO { 


	
	
/**
* Method update.
	 * @param string tableName. The name of the table
	 * @param valuePairs The columns and their values
	 * @param databaseInfo
	 * @param primaryKey String value of the primary key if provided. 
**/
	public static void update(
		String tableName,
		Map<String, String> valuePairs,
		Database databaseInfo,
		String primaryKey,
		DBConnection connection) {
		try {
			// construct a string of assignments from valuePairs e.g. "foo = ?, bar = ?, baz = ?"
			StringBuffer assignments = new StringBuffer();
			boolean previousAssignments = false;
			// copying the set to a list means we can iterate it twice in the same order
			ArrayList<String> nameList = new ArrayList<String>(valuePairs.keySet());
			Iterator<String> nameIterator= nameList.iterator();
			String elementName;
			// remember the name of the primary key column so we can avoid inserting null values in that column
			while (nameIterator.hasNext()) {
				elementName = nameIterator.next();
				if (databaseInfo.getTable(tableName).hasColumn(elementName)) {
					Column column = databaseInfo.getTable(tableName).getColumn(elementName);
					if (!column.isPrimaryKey()) { // don't update the primary key
						// add column name and value placeholder to query
						if (previousAssignments) {
							assignments.append(", ");
						}
						assignments.append(column.getName());
						assignments.append(" = ?");
						// remember to add a comma before any subsequent assignments
						previousAssignments = true;
					}
				}
			}
			// create PreparedStatement
	
			String primaryKeyColumnName =  databaseInfo.getTable(tableName).getPkColumnName();
			//String updateStatement = getUpdateStatement(tableName, valuePairs, databaseInfo, assignments.toString(), primaryKey);
			String updateStatement =  "update "
				+ tableName
				+ " set "
				+ assignments
				+ " where "
				+ primaryKeyColumnName
				+ " = ?";
	
			log.info("Statement: " + updateStatement);
	
			Connection conn = connection.getConnection();
			PreparedStatement statement = conn.prepareStatement(updateStatement);
	
			// add assignment parameter value to PreparedStatement
			nameIterator= nameList.iterator();
			int i=0;
			while (nameIterator.hasNext()) {
				elementName = nameIterator.next();
				if (databaseInfo.getTable(tableName).hasColumn(elementName)) {
					Column column = databaseInfo.getTable(tableName).getColumn(elementName);
					if (!column.isPrimaryKey()) { // don't update the primary key
						// add parameter value to query
						i++;
						String value = valuePairs.get(elementName);
						int type = column.getType();
						log.info("Adding element " + elementName + " with type=" + String.valueOf(type) + " and value={" + value + "}");
						setParameterValue(statement, i, type, value);
					}
				}
			}
			// specify the value of the primary key for the SQL "where" clause
			int primaryKeyColumnType = databaseInfo.getTable(tableName).getColumn(primaryKeyColumnName).getType();
			setParameterValue(statement, i + 1, primaryKeyColumnType, primaryKey);
	
			int rowsAffected = statement.executeUpdate();
			log.info("Rows affected " + String.valueOf(rowsAffected));

		} catch (Exception e) {
			log.error("Exception while updating row", e);
			throw new RuntimeException(e);
		} 
	}
	
	/**
	 * Set the value of a parameter to a PreparedStatement.
	 * @param statement	the statement to attach the parameter to
	 * @param i	the ordinal of the parameter in the parameter list
	 * @param type the JDBC type of the parameter
	 * @param value the value as a (possibly null) String
	 */
	private static void setParameterValue(PreparedStatement statement, int i, int type, String value) throws SQLException {
		if ((value == null) || (value.length() == 0)) {
			statement.setNull(i, type);
		} else if (type == java.sql.Types.TIMESTAMP) {
			statement.setTimestamp(
			  i,
			  java.sql.Timestamp.valueOf(value),
			  java.util.Calendar.getInstance()
			);
		} else {
			statement.setObject(
				i,
				value,
				type
			);
		}
	};


	/**
	 * Method insert.
	 * 
	 * @param string tableName. The name of the table
	 * @param valuePairs The columns and their values
	 * @param databaseInfo
	 * @param primaryKey String value of the primary key if provided. 
	 *	 */
	public static void insert(
			String tableName, 
			Map<String, String> valuePairs, 
			Database databaseInfo, 
			String primaryKey,
			DBConnection connection)
		throws SQLException {
		// construct a string of column names from valuePairs e.g. "foo, bar, baz"
		StringBuffer columnList = new StringBuffer();
		StringBuffer parameterList = new StringBuffer();
		boolean previousAssignments = false;
		// copying the set to a list means we can iterate it twice in the same order
		ArrayList<String> nameList = new ArrayList<String>(valuePairs.keySet());
		Iterator<String> nameIterator= nameList.iterator();
		String elementName;

		// remember the name of the primary key column so we can avoid inserting null values in that column
		String primaryKeyColumnName = databaseInfo.getTable(tableName).getPkColumnName();

		while (nameIterator.hasNext()) {
			elementName = nameIterator.next();

			if (databaseInfo.getTable(tableName).hasColumn(elementName)) { 
				// only attempt to update a column if it actually exists in the table
				Column column = databaseInfo.getTable(tableName).getColumn(elementName);

				// check if the element would attempt to insert a null value into a primary key column
				if (!elementName.equalsIgnoreCase(primaryKeyColumnName) || !isPrimaryKeyProvided(primaryKey) ) {
					// OK - element is not the primary key, or if it is the primary key, it's not a null value, so we can insert it

					// add column name to query, prepending a comma if there were previous columns added.
					if (previousAssignments) {
						columnList.append(", ");
						parameterList.append(", ");
					}
					columnList.append(column.getName());
					parameterList.append(" ?");
					
					// Record that we have added a column name to our insert statement
					// so that we can ensure any subsequent columns are preceded by a comma
					previousAssignments = true;
				}
			}
		}
		
		String insertStatement =  "insert into " + tableName + " ( " + columnList + " ) values ( " + parameterList + " )";

		log.info("Statement: " + insertStatement);

		Connection conn = connection.getConnection();
		PreparedStatement statement =
				conn.prepareStatement(insertStatement);
			
		// now add parameters to PreparedStatement
		nameIterator= nameList.iterator();
		int i=0;
		while (nameIterator.hasNext()) {
			elementName = nameIterator.next();
			if (databaseInfo.getTable(tableName).hasColumn(elementName)) {
				Column column = databaseInfo.getTable(tableName).getColumn(elementName);
				// don't insert a null into the primary key column
				if (!elementName.equalsIgnoreCase(primaryKeyColumnName) || !isPrimaryKeyProvided(primaryKey) ) {
					// add parameter value to query
					i++;
					String value = valuePairs.get(elementName);
					int type = column.getType();
					log.info("Adding element " + elementName + " with type=" + String.valueOf(type) + " and value={" + value + "}");
					setParameterValue(statement, i, type, value);
				}
			}
		}
		
		statement.execute();
	}
	
	private static boolean isPrimaryKeyProvided(String primaryKey) {
		return primaryKey != null && primaryKey.length() > 0;
	}

}
