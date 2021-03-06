package org.apache.spark.sql.hive

import org.apache.spark.SparkContext
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.sql._
import org.apache.spark.sql.catalyst.analysis.{Analyzer, _}
import org.apache.spark.sql.catalyst.optimizer.Optimizer
import org.apache.spark.sql.catalyst.ParserDialect
import org.apache.spark.sql.execution.ExtractPythonUDFs
import org.apache.spark.sql.execution.datasources._
import org.apache.spark.sql.extension._

/**
 * Extendable [[HiveContext]]. This context is composable with traits
 * that can provide extended parsers, resolution rules, function registries or
 * strategies.
 *
 * @param sparkContext The SparkContext.
 */
@DeveloperApi
private[hive] class ExtendableHiveContext(@transient override val sparkContext: SparkContext)
  extends HiveContext(sparkContext) with SQLContextExtensionBase {
  self =>

  override protected[sql] def getSQLDialect(): ParserDialect = extendedParserDialect

  @transient
  override protected[sql] val ddlParser: DDLParser = extendedDdlParser(sqlParser.parse)

  override def sql(sqlText: String): DataFrame = {
    /* TODO: Switch between SQL dialects (Spark SQL's, HiveQL or our extended parser. */
    DataFrame(this,
      ddlParser.parse(sqlText, exceptionOnError = false)
    )
  }

  @transient
  override protected[sql]
  lazy val functionRegistry = {
    val registry = new HiveFunctionRegistry(new SimpleFunctionRegistry)
    registerBuiltins(registry)
    registerFunctions(registry)
    registry
  }

  @transient
  override protected[sql] lazy val catalog =
    new HiveMetastoreCatalog(metadataHive, this)
      with OverrideCatalog with TemporaryFlagProxyCatalog

  /**
   * Copy of [[HiveContext]]'s [[Analyzer]] adding rules from our extensions.
   */
  @transient
  override protected[sql] lazy val analyzer: Analyzer =
    new Analyzer(catalog, functionRegistry, conf) {
      override val extendedResolutionRules = resolutionRules(this) ++
        (catalog.ParquetConversions ::
          catalog.CreateTables ::
          catalog.PreInsertionCasts ::
          ExtractPythonUDFs ::
          ResolveHiveWindowFunction ::
          PreInsertCastAndRename ::
          Nil)

      override val extendedCheckRules = Seq(
        PreWriteCheck(catalog),
        // TODO: Move this once bug #95571 is fixed.
        HierarchyUDFAnalysis(catalog)
      )
    }

  @transient
  override protected[sql] lazy val optimizer: Optimizer =
    new ExtendableOptimizer(
      earlyBatches = optimizerEarlyBatches,
      mainBatchRules = optimizerMainBatchRules
    )

  @transient
  override protected[sql] val planner: SparkPlanner with HiveStrategies =
    new SparkPlanner with HiveStrategies with ExtendedPlanner {
      def baseStrategies(hiveContext: HiveContext): Seq[Strategy] =
        Seq(
          DataSourceStrategy,
          HiveCommandStrategy(hiveContext),
          HiveDDLStrategy,
          DDLStrategy,
          TakeOrderedAndProject,
          InMemoryScans,
          HiveTableScans,
          DataSinks,
          Scripts,
          HashAggregation,
          Aggregation,
          LeftSemiJoin,
          EquiJoinSelection,
          BasicOperators,
          CartesianProduct,
          BroadcastNestedLoopJoin
        )

      override def strategies: Seq[Strategy] =
        self.strategies(this) ++
          experimental.extraStrategies ++
          baseStrategies(self)

      override val hiveContext = self
    }
}
