package org.apache.spark.sql.hierarchy

import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions._

object HierarchyRowJoinBuilder {
  def apply(
             attributes: Seq[Attribute],
             parenthoodExpression: Expression,
             startWhere: Expression, searchBy: Seq[SortOrder]
             ): HierarchyBuilder[Row, Row] = {

    val predSuccIndexes: (Int, Int) = parenthoodExpression match {
      case EqualTo(
      left@AttributeReference(ln, ldt, _, _),
      right@AttributeReference(rn, rdt, _, _)
      ) if ldt == rdt =>
        val predIndex = attributes.indexWhere(_.name == ln)
        val succIndex = attributes.indexWhere(_.name == rn)
        (predIndex, succIndex)
      case _ =>
        throw new UnsupportedOperationException(
          s"Unsupported parenthood expression: $parenthoodExpression"
        )
    }
    val predIdx = predSuccIndexes._1
    val pkIdx = predSuccIndexes._2

    val rowFunctions = HierarchyRowFunctions(attributes.map(_.dataType))
    val pk = rowFunctions.rowGet[java.lang.Long](pkIdx)
    val pred = rowFunctions.rowGet[java.lang.Long](predIdx)
    val startsWhere = rowFunctions.rowStartWhere(
      rowFunctions.bindExpression(startWhere, attributes))
    // Todo(Weidner): currently, only first ordering rule is applied:
    val ord = searchBy.isEmpty match{
      case true => null
      case false =>
        rowFunctions.rowGet[java.lang.Long](
          attributes.indexWhere(_.name ==
            searchBy.head.child.asInstanceOf[AttributeReference].name))
    }
    val init = rowFunctions.rowInit(pk)
    val modify = rowFunctions.rowModifyAndOrder(pk)

    new HierarchyJoinBuilder[Row,Row,Any](startsWhere, pk, pred, init, ord, modify)
  }
}
