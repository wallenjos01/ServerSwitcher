package org.wallentines.serverswitcher;

import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.mdcfg.sql.*;

public class Tables {

    public static final int SCHEMA_VERSION = 1;

    public static final String META_NAME = "meta";
    public static final TableSchema META_SCHEMA = TableSchema.builder()
            .withColumn("version", DataType.INTEGER)
            .build();

    public static final String SERVER_NAME = "server";
    public static final TableSchema SERVER_SCHEMA = TableSchema.builder()
            .withColumn(Column.builder("sId", DataType.INTEGER).withConstraint(Constraint.Type.AUTO_INCREMENT))
            .withColumn(Column.builder("name", DataType.VARCHAR(255)).withConstraint(Constraint.Type.NOT_NULL))
            .withColumn(Column.builder("address", DataType.VARCHAR(255)).withConstraint(Constraint.Type.NOT_NULL))
            .withColumn(Column.builder("port", DataType.SMALLINT).withConstraint(Constraint.Type.NOT_NULL))
            .withColumn("backend", DataType.VARCHAR(255))
            .withColumn("permission", DataType.VARCHAR(255))
            .withColumn("displayName", DataType.VARCHAR(1000))
            .withColumn("prefix", DataType.VARCHAR(255))
            .withColumn("icon", DataType.VARBINARY(4000))
            .withTableConstraint(TableConstraint.PRIMARY_KEY("sId"))
            .withTableConstraint(TableConstraint.UNIQUE("name"))
            .build();

    public static boolean setupTables(SQLConnection connection) {

        if(connection.hasTable(META_NAME)) {

            QueryResult res = connection.select(META_NAME).execute();
            if(res.rows() == 0) {
                connection.insert(META_NAME, META_SCHEMA)
                        .addRow(new ConfigSection().with("version", SCHEMA_VERSION))
                        .execute();
            } else {

                int schemaVersion = res.get(0).getInt("version");
                if(schemaVersion != SCHEMA_VERSION) {
                    return false;
                }
            }

        } else {
            connection.createTable(META_NAME, META_SCHEMA).execute();
            connection.insert(META_NAME, META_SCHEMA)
                    .addRow(new ConfigSection().with("version", SCHEMA_VERSION))
                    .execute();
        }

        if(!connection.hasTable(SERVER_NAME)) {
            connection.createTable(SERVER_NAME, SERVER_SCHEMA).execute();
        }

        return true;
    }

}
