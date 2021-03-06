package org.apache.spark.sql.execution.datasources

import org.apache.spark.sql.catalyst.TableIdentifier
import org.apache.spark.sql.execution.RunnableCommand
import org.apache.spark.sql.execution.datasources._
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Row, SQLContext}

/**
 * This command is used to register persistent tables if the datasource is capable of reporting
 * according to that.
 */
private[sql]
case class CreateTableUsingTemporaryAwareCommand(
    tableIdentifier: TableIdentifier,
    userSpecifiedSchema: Option[StructType],
    partitionColumns: Array[String],
    partitioningFunction: Option[String],
    partitioningColumns: Option[Seq[String]],
    provider: String,
    options: Map[String, String],
    isTemporary: Boolean,
    allowExisting: Boolean)
  extends RunnableCommand {

  def run(sqlContext: SQLContext): Seq[Row] = {

    val dataSource: Any = ResolvedDataSource.lookupDataSource(provider).newInstance()

    val resolved: ResolvedDataSource = resolveDataSource(sqlContext, userSpecifiedSchema,
      partitionColumns, partitioningFunction, partitioningColumns, dataSource, options,
      isTemporary, allowExisting)
    // check if this class implements the DatabaseRelation Provider trait
    // this is also checked in the corresponding strategy CreatePersistentTableStrategy
    // however this class could also be called from somewhere else
    dataSource match {
      case _: TemporaryAndPersistentNature =>
        // make sure we register that properly in the catalog
        sqlContext.registerDataFrameAsTable(
          DataFrame(sqlContext, LogicalRelation(resolved.relation)), tableIdentifier.table)
        Seq.empty
      case _ if !isTemporary =>
        throw new RuntimeException("Datasource does not support non temporary tables!")
    }
  }

  /**
   * Returns a resolved datasource with temporary or persistent table creation handling.
   */
  private def resolveDataSource(sqlContext: SQLContext,
                                userSpecifiedSchema: Option[StructType],
                                partitionColumns: Array[String],
                                partitioningFunction: Option[String],
                                partitioningColumns: Option[Seq[String]],
                                dataSource: Any,
                                options: Map[String, String],
                                isTemporary: Boolean,
                                allowExisting: Boolean): ResolvedDataSource = {

    dataSource match {
      case drp: PartitionedRelationProvider =>
        if (userSpecifiedSchema.isEmpty) {
          new ResolvedDataSource(drp.getClass,
            drp.createRelation(sqlContext,
              new CaseInsensitiveMap(options), partitioningFunction, partitioningColumns,
              isTemporary, allowExisting))
        } else {
          new ResolvedDataSource(drp.getClass,
            drp.createRelation(sqlContext, new CaseInsensitiveMap(options), userSpecifiedSchema.get,
              partitioningFunction, partitioningColumns, isTemporary, allowExisting))
        }
      case drp: TemporaryAndPersistentSchemaRelationProvider if userSpecifiedSchema.nonEmpty =>
            new ResolvedDataSource(drp.getClass,
              drp.createRelation(
                sqlContext,
                new CaseInsensitiveMap(options),
                userSpecifiedSchema.get,
                isTemporary,
                allowExisting))
      case drp: TemporaryAndPersistentRelationProvider =>
        new ResolvedDataSource(drp.getClass,
          drp.createRelation(sqlContext,
            new CaseInsensitiveMap(options), isTemporary, allowExisting))
      case _ => ResolvedDataSource(sqlContext, userSpecifiedSchema,
        partitionColumns, provider, options)
    }
  }
}
