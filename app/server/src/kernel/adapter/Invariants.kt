package dbest.kernel.adapter

import dbest.kernel.util.isBlank
import dbest.kernel.util.isEmpty

fun requireSourceAlias(alias: String) =
    require(!isBlank(alias), { "O alias nao pode ser uma string vazia" })

fun requireProjectColumns(columns: List<String>) =
    require(!isEmpty(columns), { "Eh necessario selecionar no minimo uma coluna para projecao" })

fun requireRemoveColumns(columns: List<String>, alias: String) {
    require(!isEmpty(columns), { "RemoveColumns precisa de pelo menos uma coluna" })
    require(!isBlank(alias), { "RemoveColumns precisa de um alias para sua saida" })
}

fun requireSortKeys(keys: List<SortKey>) {
    require(!isEmpty(keys), { "Sort precisa de pelo menos um parametro" })
    val ascending = keys.first().ascending
    require(keys.all { key -> key.ascending == ascending }, {
        "O mecanismo atual aceita uma unica direcao para todas as colunas de ordenacao"
    })
}

fun requireLimitBounds(count: Int, offset: Int) {
    require(count > 0, { "Limit precisa ser positivo" })
    require(offset >= 0, { "Offset nao pode ser negativo" })
}

fun requireAliasPair(from: String, to: String) =
    require(!isBlank(from) && !isBlank(to), { "Alias precisa do alias atual e do novo alias da fonte" })

fun requireCollapseAlias(alias: String) =
    require(!isBlank(alias), { "Collapse precisa de um alias para a fonte unificada" })

fun requireExplode(column: String, delimiter: String) {
    require(!isBlank(column), { "Explode precisa de uma coluna" })
    require(!isEmpty(delimiter), { "Explode precisa de um delimitador" })
}

fun requireRowNumber(alias: String, column: String) {
    require(!isBlank(alias), { "RowNumber precisa de um alias de fonte para sua saida" })
    require(!isBlank(column), { "RowNumber precisa do nome de uma coluna" })
}

fun requireAggregate(alias: String, aggregates: List<Agg>) {
    require(!isBlank(alias), { "Agregacao precisa de um alias de fonte para sua saida" })
    require(!isEmpty(aggregates), { "Agregacao precisa de pelo menos um agregado" })
}

fun requireJoinTerms(on: List<JoinTerm>) =
    require(!isEmpty(on), { "Join precisa de pelo menos um termo de igualdade" })
