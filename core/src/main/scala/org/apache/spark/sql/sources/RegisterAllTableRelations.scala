package org.apache.spark.sql.sources

import org.apache.spark.sql.SQLContext

trait RegisterAllTableRelations {

  def getAllTableRelations(
                            sqlContext: SQLContext,
                            options: Map[String, String]): Map[String, LogicalPlanSource]

  def getTableRelation(tableName: String,
                       sqlContext: SQLContext,
                       options: Map[String, String]): Option[LogicalPlanSource]
}
