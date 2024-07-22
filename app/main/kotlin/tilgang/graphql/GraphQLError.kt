package tilgang.graphql

internal data class GraphQLError(
    val message: String,
    val locations: List<GraphQLErrorLocation>,
    val path: List<String>?,
    val extensions: GraphQLErrorExtension
)

internal data class GraphQLErrorExtension(
    val code: String?,
    val classification: String
)

internal data class GraphQLErrorLocation(
    val line: Int?,
    val column: Int?
)
