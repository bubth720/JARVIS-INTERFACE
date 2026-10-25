package com.jarvis.interfaceapp.workspace

import com.jarvis.interfaceapp.domain.*
import com.jarvis.interfaceapp.storage.LayoutCodec
import com.jarvis.interfaceapp.ui.identity.*
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Sidecar version1, never overwrites dashboard.json or CORE-owned identity. */
object WorkspaceCodec {
    const val MAX_BYTES=2*1024*1024
    private fun number(o:JsonObject,k:String)=o.getValue(k).jsonPrimitive.let { require(!it.isString);it.int }
    private fun flag(o:JsonObject,k:String)=o.getValue(k).jsonPrimitive.let { require(!it.isString);it.boolean }
    private fun str(e:JsonElement)=e.jsonPrimitive.let { require(it.isString);it.content }
    fun strict(bytes:ByteArray):JsonObject {
        require(bytes.size<=MAX_BYTES)
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        var depth=0;var quoted=false;var escaped=false
        text.forEach { c -> if(quoted) { if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false }
            else when(c) {'"'->quoted=true;'{','['->{depth++;require(depth<=32)};'}',']'->depth--} }
        val result=Json.parseToJsonElement(text).jsonObject
        val keys=ArrayDeque<MutableSet<String>>();var i=0
        while(i<text.length) { when(text[i]) {
            '{'->keys.addLast(mutableSetOf());'}'->keys.removeLast()
            '"'->{val start=i++;while(text[i]!='"'){if(text[i]=='\\')i++;i++};val end=++i
                while(i<text.length && text[i].isWhitespace())i++
                if(i<text.length && text[i]==':')require(keys.last().add(Json.parseToJsonElement(text.substring(start,end)).jsonPrimitive.content))
                continue}
        };i++ };return result
    }
    fun decode(bytes:ByteArray):Workspace {
        val r=strict(bytes);require(number(r,"version")==1)
        val v=r.getValue("visual").jsonObject
        val visual=VisualPreferences(LocalTheme.valueOf(str(v.getValue("theme"))),number(v,"textPercent"),number(v,"elementPercent"),
            flag(v,"reduceMotion"),flag(v,"transitions"),DisplayMode.valueOf(str(v.getValue("mode"))),
            v.getValue("visible").jsonArray.map { PanelId.valueOf(str(it)) }.also { require(it.distinct().size==it.size) }.toSet())
        val p=r.getValue("persona").jsonObject
        fun parameters(o:JsonObject)=PersonaParameters(number(o,"frankness"),number(o,"humour"),number(o,"initiative"),flag(o,"tutoiement"),PersonaStyle.valueOf(str(o.getValue("style"))))
        val draft=ProfileDraft(str(p.getValue("name")),PersonaPreset.valueOf(str(p.getValue("preset"))),parameters(p.getValue("defaults").jsonObject),
            p.getValue("overrides").jsonObject.map { (k,x)->PersonaContext.valueOf(k) to parameters(x.jsonObject) }.toMap(),
            p.getValue("interests").jsonArray.map { SimulatedInterest.valueOf(str(it)) }.toSet(),
            p.getValue("rules").jsonArray.map { LexicalRule(LexicalToken.valueOf(str(it.jsonObject.getValue("source"))),LexicalToken.valueOf(str(it.jsonObject.getValue("target")))) })
        val layouts=r.getValue("layouts").jsonObject.mapValues { (_,l)->LayoutCodec.decode(l.toString().toByteArray()).also { require(it.issue==null) }.layouts }
        val history=r.getValue("history").jsonArray.map { val o=it.jsonObject;ArchivedMessage(str(o.getValue("id")),str(o.getValue("text")),flag(o,"user"),flag(o,"simulation"),(if("sources" in o)o.getValue("sources").jsonArray else JsonArray(emptyList())).map {x->DocumentSource(str(x.jsonObject.getValue("title")),str(x.jsonObject.getValue("excerpt")))}) }
        return Workspace(visual,LocalPersona(draft,number(p,"detail")),layouts,flag(r,"keepHistory"),history,
            r.getValue("packages").jsonObject.map { (k,x)->ModuleId.valueOf(k) to str(x) }.toMap())
    }
    fun encode(w:Workspace,previous:JsonObject=JsonObject(emptyMap())):ByteArray {
        fun params(p:PersonaParameters)=buildJsonObject { put("frankness",p.frankness);put("humour",p.humour);put("initiative",p.initiative);put("tutoiement",p.tutoiement);put("style",p.style.name) }
        fun merge(k:String,known:JsonObject)=JsonObject((previous[k] as? JsonObject).orEmpty()+known)
        val p=w.persona.profile
        val root=JsonObject(previous+mapOf("version" to JsonPrimitive(1),"visual" to merge("visual",buildJsonObject {
            put("theme",w.visual.theme.name);put("textPercent",w.visual.textPercent);put("elementPercent",w.visual.elementPercent)
            put("reduceMotion",w.visual.reduceMotion);put("transitions",w.visual.transitions);put("mode",w.visual.mode.name)
            put("visible",JsonArray(w.visual.visible.sortedBy { it.name }.map { JsonPrimitive(it.name) }))
        }),"persona" to merge("persona",buildJsonObject {
            put("name",p.name);put("preset",p.preset.name);put("defaults",params(p.defaults));put("detail",w.persona.detail)
            put("overrides",JsonObject(p.overrides.mapKeys { it.key.name }.mapValues { params(it.value) }))
            put("interests",JsonArray(p.interests.sortedBy { it.name }.map { JsonPrimitive(it.name) }))
            put("rules",JsonArray(p.rules.map { buildJsonObject { put("source",it.source.name);put("target",it.target.name) } }))
        }),"layouts" to JsonObject(w.layouts.mapValues { (_,l)->LayoutCodec.withLayouts(LayoutCodec.defaults(),l).root!! }),
        "keepHistory" to JsonPrimitive(w.keepHistory),"history" to JsonArray(w.history.map { buildJsonObject {
            put("id",it.id);put("text",it.text);put("user",it.user);put("simulation",it.simulation);put("sources",JsonArray(it.sources.map {source->buildJsonObject {put("title",source.title);put("excerpt",source.excerpt)} }))
        } }),"packages" to JsonObject(w.packages.mapKeys { it.key.name }.mapValues { JsonPrimitive(it.value) })))
        return root.toString().toByteArray().also { require(it.size<=MAX_BYTES) }
    }
    fun exportLayouts(layouts:Layouts):ByteArray { validateLayouts(layouts);return LayoutCodec.encode(LayoutCodec.withLayouts(LayoutCodec.defaults(),layouts)) }
    fun importLayouts(bytes:ByteArray):Layouts {
        require(bytes.size<=LayoutCodec.MAX_BYTES)
        return LayoutCodec.decode(bytes).also { require(it.issue==null) }.layouts.also(::validateLayouts)
    }
}
