package team.bjtuss.bjtuselfservice.shared.data.home

import team.bjtuss.bjtuselfservice.shared.data.homework.parseStrictJsonObject
import team.bjtuss.bjtuselfservice.shared.data.homework.string
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeStatus

sealed interface HomeStatusParseResult {
    data class Success(val status: HomeStatus) : HomeStatusParseResult
    data class Failure(val field: String) : HomeStatusParseResult
}

fun parseHomeStatusJson(body: String): HomeStatusParseResult {
    val root = parseStrictJsonObject(body) ?: return HomeStatusParseResult.Failure("root")
    val mail = root.string("newmail_count")?.takeIf(String::isNotBlank)
        ?: return HomeStatusParseResult.Failure("newmail_count")
    val card = root.string("ecard_yuer")?.takeIf(String::isNotBlank)
        ?: return HomeStatusParseResult.Failure("ecard_yuer")
    val network = root.string("net_fee")?.takeIf(String::isNotBlank)
        ?: return HomeStatusParseResult.Failure("net_fee")
    return HomeStatusParseResult.Success(HomeStatus(mail, card, network))
}
