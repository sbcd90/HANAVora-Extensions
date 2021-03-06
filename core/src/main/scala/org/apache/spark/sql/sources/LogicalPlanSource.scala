package org.apache.spark.sql.sources

import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.execution.datasources.{CreatePersistentViewCommand, LogicalRelation}
import org.apache.spark.sql.{DataFrame, SQLContext}

/** Source from which a [[LogicalPlan]] can be obtained. */
trait LogicalPlanSource {
  /** Instantiates a [[LogicalPlan]] with the given sqlContext.
    *
    * @param sqlContext The sqlContext
    * @return The created [[LogicalPlan]]
    */
  def logicalPlan(sqlContext: SQLContext): LogicalPlan
}

/** Source of a [[org.apache.spark.sql.DataFrame]] from a BaseRelation.
  *
  * @param baseRelation The baseRelation from which the [[DataFrame]] is created.
  */
case class BaseRelationSource(baseRelation: BaseRelation) extends LogicalPlanSource {
  def logicalPlan(sqlContext: SQLContext): LogicalPlan = {
    LogicalRelation(baseRelation)
  }
}

/** Source of a [[org.apache.spark.sql.DataFrame]] from a create persistent view statement
  *
  * @param createViewStatement The sql query string.
  */
case class CreatePersistentViewSource(createViewStatement: String) extends LogicalPlanSource {
  def logicalPlan(sqlContext: SQLContext): LogicalPlan = {
    sqlContext.parseSql(createViewStatement) match {
      case CreatePersistentViewCommand(_, plan, _, _, _) =>
        plan
      case unknown =>
        throw new RuntimeException(s"Could not extract view query from $unknown")
    }
  }
}

