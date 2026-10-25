package com.jarvis.interfaceapp.workspace

import androidx.datastore.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.*
import kotlinx.serialization.json.JsonObject

data class WorkspaceRecord(val value:Workspace=Workspace(),val raw:JsonObject=JsonObject(emptyMap()))
object WorkspaceSerializer:Serializer<WorkspaceRecord> {
    override val defaultValue=WorkspaceRecord()
    override suspend fun readFrom(input:InputStream):WorkspaceRecord {
        val buffer=ByteArrayOutputStream();val chunk=ByteArray(4096)
        while(buffer.size()<=WorkspaceCodec.MAX_BYTES) {
            val count=input.read(chunk,0,minOf(chunk.size,WorkspaceCodec.MAX_BYTES+1-buffer.size()))
            if(count<0)break
            buffer.write(chunk,0,count)
        }
        val bytes=buffer.toByteArray()
        return try { WorkspaceRecord(WorkspaceCodec.decode(bytes),WorkspaceCodec.strict(bytes)) }
        catch(e:Exception) { throw CorruptionException("Workspace incompatible ou illisible — original conservé",e) }
    }
    override suspend fun writeTo(t:WorkspaceRecord,output:OutputStream) { output.write(WorkspaceCodec.encode(t.value,t.raw)) }
}
interface WorkspaceRepository {
    suspend fun load():Workspace
    suspend fun change(transform:(Workspace)->Workspace):Workspace
}
class DataStoreWorkspaceRepository(file:()->File,scope:CoroutineScope):WorkspaceRepository {
    private val store=DataStoreFactory.create(serializer=WorkspaceSerializer,scope=scope,produceFile=file)
    override suspend fun load()=withContext(Dispatchers.IO) {store.data.first().value}
    override suspend fun change(transform:(Workspace)->Workspace)=withContext(Dispatchers.IO) {store.updateData { old ->
        val next=transform(old.value);WorkspaceCodec.encode(next,old.raw);old.copy(value=next)
    }.value}
}
data class WorkspaceUi(val value:Workspace=Workspace(),val ready:Boolean=false,val saving:Boolean=false,
    val error:String?=null,val durable:Boolean=false)
/** Single ordered writer. Values become visible as durable only after DataStore ACK. */
class WorkspaceController(private val repository:WorkspaceRepository,scope:CoroutineScope) {
    private val mutable=MutableStateFlow(WorkspaceUi());val state=mutable.asStateFlow()
    private data class Write(val transform:(Workspace)->Workspace,val completion:CompletableDeferred<Unit>?=null)
    private val commands=Channel<Write>(Channel.UNLIMITED)
    init { scope.launch {
        try { mutable.value=WorkspaceUi(repository.load(),ready=true,durable=true) }
        catch(e:Exception) { if(e is CancellationException)throw e;mutable.value=WorkspaceUi(error="Lecture impossible : fichier conservé, écriture désactivée") }
        for(command in commands) {
            if(command.completion!=null) { command.completion.complete(Unit);continue }
            if(!mutable.value.ready)continue
            mutable.value=mutable.value.copy(saving=true,error=null,durable=false)
            try { val result=repository.change(command.transform);mutable.value=WorkspaceUi(result,ready=true,durable=true) }
            catch(e:Exception) { if(e is CancellationException)throw e;mutable.value=mutable.value.copy(saving=false,error="Écriture refusée : modification non enregistrée",durable=false) }
        }
    } }
    fun change(transform:(Workspace)->Workspace) { commands.trySend(Write(transform)).getOrThrow() }
    suspend fun awaitIdle() { val end=CompletableDeferred<Unit>();commands.send(Write({it},end));end.await() }
    fun archive(messages:List<ArchivedMessage>) { change { if(it.keepHistory)it.copy(history=(it.history+messages).distinctBy { m->m.id }.takeLast(200)) else it } }
}
