package org.arend.documentation

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import org.arend.documentation.ArendKeyword.*
import org.arend.documentation.ArendKeyword.Companion.toArendKeyword
import org.arend.documentation.ArendKeywordSection.*
import org.arend.psi.ArendElementTypes
import org.arend.psi.initTokenSet
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.UnknownHostException

enum class ArendKeywordSection(val sectionName: String) {
    OPEN_SECTION("open-commands"),
    IMPORT_SECTION("import-commands"),
    TRUNCATED_SECTION("truncation"),
    CONS_SECTION("constructor-synonyms"),
    LEMMA_SECTION("lemmas"),
    SFUNC_SECTION("sfunc"),
    CLASSIFYING_SECTION("classifying-fields"),
    STRICT_SECTION("strict-parameters"),
    ALIAS_SECTION("aliases"),
    PROPERTY_SECTION("properties"),
    OVERRIDE_SECTION("override"),
    DEFAULT_SECTION("default"),
    EXTENDS_SECTION("extensions"),
    MODULE_SECTION("modules"),
    INSTANCES_SECTION("instances"),
    LEVELS_SECTION("level-polymorphism"),
    PLEVELS_SECTION("level-parameters"),
    WITH_SECTION("pattern-matching"),
    ELIM_SECTION("elim"),
    COWITH_SECTION("copattern-matching"),
    WHERE_SECTION("where-blocks"),
    INFIX_SECTION("infix-operators"),
    FIX_SECTION("precedence"),
    THIS_SECTION("this"),
    LETS_SECTION("strict-let-expressions"),
    HAVE_SECTION("have"),
    SCASE_SECTION("scase"),
    LP_SECTION("level-polymorphism")
}

enum class ArendKeyword(val type: IElementType, val section: ArendKeywordSection?) {
    OPEN(ArendElementTypes.OPEN_KW, OPEN_SECTION),
    IMPORT(ArendElementTypes.IMPORT_KW, IMPORT_SECTION),
    HIDING(ArendElementTypes.HIDING_KW, OPEN_SECTION),
    AS(ArendElementTypes.AS_KW, OPEN_SECTION),
    USING(ArendElementTypes.USING_KW, OPEN_SECTION),
    TRUNCATED(ArendElementTypes.TRUNCATED_KW, TRUNCATED_SECTION),
    DATA(ArendElementTypes.DATA_KW, null),
    CONS(ArendElementTypes.CONS_KW, CONS_SECTION),
    FUNC(ArendElementTypes.FUNC_KW, null),
    LEMMA(ArendElementTypes.LEMMA_KW, LEMMA_SECTION),
    AXIOM(ArendElementTypes.AXIOM_KW, LEMMA_SECTION),
    SFUNC(ArendElementTypes.SFUNC_KW, SFUNC_SECTION),
    TYPE(ArendElementTypes.TYPE_KW, null),
    CLASS(ArendElementTypes.CLASS_KW, null),
    RECORD(ArendElementTypes.RECORD_KW, null),
    META(ArendElementTypes.META_KW, null),
    CLASSIFYING(ArendElementTypes.CLASSIFYING_KW, CLASSIFYING_SECTION),
    NO_CLASSIFYING(ArendElementTypes.NO_CLASSIFYING_KW, CLASSIFYING_SECTION),
    STRICT(ArendElementTypes.STRICT_KW, STRICT_SECTION),
    ALIAS(ArendElementTypes.ALIAS_KW, ALIAS_SECTION),
    FIELD(ArendElementTypes.FIELD_KW, null),
    PROPERTY(ArendElementTypes.PROPERTY_KW, PROPERTY_SECTION),
    OVERRIDE(ArendElementTypes.OVERRIDE_KW, OVERRIDE_SECTION),
    DEFAULT(ArendElementTypes.DEFAULT_KW, DEFAULT_SECTION),
    EXTENDS(ArendElementTypes.EXTENDS_KW, EXTENDS_SECTION),
    MODULE(ArendElementTypes.MODULE_KW, MODULE_SECTION),
    INSTANCE(ArendElementTypes.INSTANCE_KW, INSTANCES_SECTION),
    USE(ArendElementTypes.USE_KW, null),
    COERCE(ArendElementTypes.COERCE_KW, null),
    LEVEL(ArendElementTypes.LEVEL_KW, null),
    LEVELS(ArendElementTypes.LEVELS_KW, LEVELS_SECTION),
    PLEVELS(ArendElementTypes.PLEVELS_KW, PLEVELS_SECTION),
    HLEVELS(ArendElementTypes.HLEVELS_KW, PLEVELS_SECTION),
    BOX(ArendElementTypes.BOX_KW, null),
    EVAL(ArendElementTypes.EVAL_KW, SFUNC_SECTION),
    PEVAL(ArendElementTypes.PEVAL_KW, SFUNC_SECTION),
    WITH(ArendElementTypes.WITH_KW, WITH_SECTION),
    ELIM(ArendElementTypes.ELIM_KW, ELIM_SECTION),
    COWITH(ArendElementTypes.COWITH_KW, COWITH_SECTION),
    WHERE(ArendElementTypes.WHERE_KW, WHERE_SECTION),
    INFIX(ArendElementTypes.INFIX_NON_KW, INFIX_SECTION),
    INFIX_LEFT(ArendElementTypes.INFIX_LEFT_KW, INFIX_SECTION),
    INFIX_RIGHT(ArendElementTypes.INFIX_RIGHT_KW, INFIX_SECTION),
    FIX(ArendElementTypes.NON_ASSOC_KW, FIX_SECTION),
    FIX_LEFT(ArendElementTypes.LEFT_ASSOC_KW, FIX_SECTION),
    FIX_RIGHT(ArendElementTypes.RIGHT_ASSOC_KW, FIX_SECTION),
    NEW(ArendElementTypes.NEW_KW, null),
    THIS(ArendElementTypes.THIS_KW, THIS_SECTION),
    PI(ArendElementTypes.PI_KW, null),
    SIGMA(ArendElementTypes.SIGMA_KW, null),
    LAM(ArendElementTypes.LAM_KW, null),
    LET(ArendElementTypes.LET_KW, null),
    LETS(ArendElementTypes.LETS_KW, LETS_SECTION),
    HAVE(ArendElementTypes.HAVE_KW, HAVE_SECTION),
    HAVES(ArendElementTypes.HAVES_KW, HAVE_SECTION),
    IN(ArendElementTypes.IN_KW, null),
    CASE(ArendElementTypes.CASE_KW, null),
    SCASE(ArendElementTypes.SCASE_KW, SCASE_SECTION),
    RETURN(ArendElementTypes.RETURN_KW, null),
    LP(ArendElementTypes.LP_KW, LP_SECTION),
    LH(ArendElementTypes.LH_KW, LP_SECTION),
    SUC(ArendElementTypes.SUC_KW, LP_SECTION),
    MAX(ArendElementTypes.MAX_KW, LP_SECTION),
    OO(ArendElementTypes.OO_KW, LP_SECTION),
    PROP(ArendElementTypes.PROP_KW, null),
    SET(ArendElementTypes.SET, null),
    UNIVERSE(ArendElementTypes.UNIVERSE, null),
    TRUNCATED_UNIVERSE(ArendElementTypes.TRUNCATED_UNIVERSE, null),
    PRIVATE(ArendElementTypes.PRIVATE_KW, null),
    PROTECTED(ArendElementTypes.PROTECTED_KW, null)
    ;

    companion object {
        private val keywordTypes = entries.associateBy { it.type }

        fun PsiElement?.toArendKeyword() = keywordTypes[this.elementType]

        fun PsiElement?.isArendKeyword(): Boolean = ArendKeyword.keywordTypes.contains(this.elementType)

        val AREND_KEYWORDS = initTokenSet(entries.map { it.type })
    }
}

private val functionHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "functions")
private val moduleHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "modules")
private val dataHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "data")
private val typesHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "types")
private val classesHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "classes")
private val recordsHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "records")
private val metasHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "metas")
private val parametersHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "parameters")
private val definitionsHtml = ArendKeywordHtml(DEFINITION_CHAPTER, null)
private val coercionHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "coercion")
private val levelHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "level")
private val universesHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "universes")
private val classExtHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "class-ext")
private val piHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "pi")
private val sigmaHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "sigma")
private val letHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "let")
private val caseHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "case")

data class ArendKeywordHtmlSection(val id: String, val index: Int)

class ArendKeywordHtml(val chapter: String, val folder: String?) {
    var isUnknownHostException = false
    private fun initParagraphs(doc: Document?) = doc?.select("section")?.first()?.children() ?: emptyList()

    private fun initSections(paragraphs: List<Element>) = paragraphs.mapIndexedNotNull { index: Int, element: Element? ->
        if (element?.id().isNullOrEmpty()) {
            null
        } else {
            ArendKeywordHtmlSection(element?.id()!!, index)
        }
    }

    var paragraphs = initParagraphs(
        try {
            Jsoup.connect(AREND_DOCUMENTATION_BASE_PATH + chapter + (folder ?: "")).get()
        } catch (e: UnknownHostException) {
            isUnknownHostException = true
            null
        } catch (e: Throwable) {
            null
        }
    )
    var sections = initSections(paragraphs)

    fun updateConnection() {
        try {
            Jsoup.connect(AREND_DOCUMENTATION_BASE_PATH + chapter + (folder ?: "")).get()
        } catch (e: Throwable) {
            null
        }?.let {
            isUnknownHostException = false
            paragraphs = initParagraphs(it)
            sections = initSections(paragraphs)
        }

    }
}

private fun getStartAndFinishSection(html: ArendKeywordHtml?, arendKeyword: ArendKeyword?): List<Element> {
    val sectionName = arendKeyword?.section?.sectionName ?: return emptyList()
    val paragraphs = html?.paragraphs ?: return emptyList()
    val sections = html.sections

    val indexOfLemmaSection = sections.indexOf(sections.find { it.id == sectionName })
    val sectionStart = sections[indexOfLemmaSection].index + 1
    val sectionFinish = if (indexOfLemmaSection == sections.lastIndex) {
        paragraphs.size
    } else {
        sections[indexOfLemmaSection + 1].index
    }
    return paragraphs.subList(sectionStart, sectionFinish)
}

internal fun getArendKeywordHtml(arendKeyword: ArendKeyword?) =
    when (arendKeyword) {
        OPEN, HIDING, AS, USING, IMPORT, MODULE, WHERE -> moduleHtml
        TRUNCATED, DATA, CONS -> dataHtml
        FUNC, LEMMA, AXIOM, SFUNC, EVAL, PEVAL, WITH, ELIM, COWITH -> functionHtml
        TYPE -> typesHtml
        CLASS, CLASSIFYING, NO_CLASSIFYING, INSTANCE -> classesHtml
        RECORD, FIELD, PROPERTY, OVERRIDE, DEFAULT, EXTENDS, THIS -> recordsHtml
        META -> metasHtml
        STRICT -> parametersHtml
        ALIAS, INFIX, INFIX_LEFT, INFIX_RIGHT, FIX, FIX_LEFT, FIX_RIGHT -> definitionsHtml
        USE, COERCE -> coercionHtml
        LEVEL -> levelHtml
        LEVELS, PLEVELS, HLEVELS, LP, LH, SUC, MAX, OO, PROP, SET, UNIVERSE, TRUNCATED_UNIVERSE -> universesHtml
        NEW -> classExtHtml
        PI, LAM -> piHtml
        SIGMA -> sigmaHtml
        LET, LETS, HAVE, HAVES, IN -> letHtml
        CASE, SCASE, RETURN -> caseHtml
        BOX, PRIVATE, PROTECTED, null -> null
    }

internal fun StringBuilder.getDescriptionForKeyword(psiElement: PsiElement) {
    val arendKeyword = psiElement.toArendKeyword()
    val arendKeywordHtml = getArendKeywordHtml(arendKeyword)
    val paragraphs = arendKeywordHtml?.paragraphs
    val sections = arendKeywordHtml?.sections

    val sectionElements = if (arendKeywordHtml?.isUnknownHostException == true) {
        emptyList()
    } else {
        when (arendKeyword) {
            DATA, TYPE, CLASS, RECORD, META, FIELD, USE, COERCE, PI, SIGMA, LAM, LET, IN, CASE, RETURN, PROP, SET, UNIVERSE, TRUNCATED_UNIVERSE ->
                paragraphs?.subList(AREND_SECTION_START, sections?.firstOrNull()?.index ?: paragraphs.size)
            FUNC -> paragraphs?.subList(AREND_SECTION_START, sections?.firstOrNull()?.index?.minus(1) ?: paragraphs.size)
            LEVEL, NEW -> paragraphs?.subList(AREND_SECTION_START, paragraphs.size)

            else -> getStartAndFinishSection(arendKeywordHtml, arendKeyword)
        } ?: emptyList()
    }

    for (element in sectionElements) {
        processElement(psiElement.project, element, arendKeywordHtml?.chapter, arendKeywordHtml?.folder)
    }
    if (arendKeywordHtml?.isUnknownHostException == true) {
        append("There is no internet connection to get the documentation")
        arendKeywordHtml.updateConnection()
    }
    when (arendKeyword) {
        BOX, PRIVATE, PROTECTED, null -> return
        else -> {
            wrapTag("hr") {
                addLink(arendKeywordHtml, arendKeyword)
            }
        }
    }
}

private fun StringBuilder.addLink(arendKeywordHtml: ArendKeywordHtml?, arendKeyword: ArendKeyword?) {
    append("See the documentation: <a href=\"${AREND_DOCUMENTATION_BASE_PATH + (arendKeywordHtml?.chapter ?: "") + (arendKeywordHtml?.folder ?: "")
            + (arendKeyword?.section?.sectionName?.let { "#$it" } ?: "")}\">${arendKeyword?.type?.debugName}</a>")
}
