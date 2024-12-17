package tilgang.integrasjoner.nom

import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.error.RestResponseHandler
import tilgang.graphql.GraphQLError
import tilgang.integrasjoner.pdl.PdlResponse
import java.io.InputStream
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class NomResponseHandler : RestResponseHandler<InputStream> {

    private val defaultResponseHandler = DefaultResponseHandler()

    override fun <R> håndter(
        request: HttpRequest,
        response: HttpResponse<InputStream>,
        mapper: (InputStream, HttpHeaders) -> R
    ): R? {
        val respons = defaultResponseHandler.håndter(request, response, mapper)

        if (respons != null && respons is PdlResponse) {
            if (respons.errors?.isNotEmpty() == true) {
                throw NomQueryException(
                    String.format(
                        "Feil %s ved GraphQL oppslag mot %s",
                        respons.errors.map(GraphQLError::message).joinToString(), request.uri()
                    )
                )
            }
        }

        return respons
    }

    override fun bodyHandler(): HttpResponse.BodyHandler<InputStream> {
        return defaultResponseHandler.bodyHandler()
    }
}

class NomQueryException(msg: String) : RuntimeException(msg)