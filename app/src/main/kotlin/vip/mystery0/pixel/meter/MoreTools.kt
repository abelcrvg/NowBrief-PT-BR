package com.kakao.taxi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kakao.taxi.data.model.BriefState
import com.kakao.taxi.data.repository.DataStoreRepository
import com.kakao.taxi.data.repository.NetworkRepository
import com.kakao.taxi.data.repository.dataStore
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private data class LocalTask(val id: String, val title: String, val done: Boolean)
private data class ChatLine(val question: String, val answer: String)

@Composable
fun MoreToolsTab(state: BriefState, networkRepo: NetworkRepository, onArticleClick: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { DataStoreRepository(context.dataStore) }
    val scope = rememberCoroutineScope()
    val favorites by store.favoriteArticles.collectAsState(initial = emptyList())
    val tasksRaw by store.productivityTasksJson.collectAsState(initial = "")
    val tasks = remember(tasksRaw) { decodeTasks(tasksRaw) }
    val isDark by networkRepo.isDarkThemeEnabled.collectAsState()
    val isNeo by networkRepo.isNeoThemeEnabled.collectAsState()
    var newTask by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var chat by remember { mutableStateOf(listOf<ChatLine>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Seu espaço", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Ferramentas pessoais, sem criar uma conta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            MoreSectionCard("Assistente IA", "Pesquisa aberta e contexto das notícias.") {
                if (chat.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        chat.takeLast(6).forEach { line ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Você", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(line.question, style = MaterialTheme.typography.bodyMedium)
                                Text("NowBrief", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(line.answer, style = MaterialTheme.typography.bodyMedium)
                                HorizontalDivider()
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = question, onValueChange = { question = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Pergunte qualquer coisa") },
                    placeholder = { Text("Ex.: explique a principal notícia de hoje") }, maxLines = 3
                )
                Button(
                    onClick = {
                        val prompt = question.trim()
                        if (prompt.isNotEmpty() && !isThinking) {
                            question = ""; isThinking = true
                            scope.launch {
                                val answer = try { smartNewsAnswer(prompt, state.news) }
                                catch (_: Exception) { "Não consegui pesquisar agora. Verifique a conexão e tente novamente." }
                                chat = chat + ChatLine(prompt, answer)
                                isThinking = false
                            }
                        }
                    },
                    enabled = question.isNotBlank() && !isThinking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isThinking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isThinking) "Pesquisando…" else "Perguntar")
                }
                Text("Sem assinatura: usa pesquisa aberta. Confira informações importantes em fontes confiáveis.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            MoreSectionCard("Agenda e tarefas", "${tasks.count { it.done }} de ${tasks.size} concluídas") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newTask, onValueChange = { newTask = it },
                        modifier = Modifier.weight(1f), label = { Text("Nova tarefa") }, singleLine = true)
                    IconButton(onClick = {
                        val title = newTask.trim()
                        if (title.isNotEmpty()) {
                            scope.launch { store.saveProductivityTasksJson(encodeTasks(tasks + LocalTask(UUID.randomUUID().toString(), title, false))) }
                            newTask = ""
                        }
                    }, enabled = newTask.isNotBlank()) { Icon(Icons.Default.Send, contentDescription = "Adicionar tarefa") }
                }
                if (tasks.isEmpty()) Text("Adicione uma tarefa para organizar seu dia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                tasks.forEach { task ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.done, onCheckedChange = { checked ->
                            scope.launch { store.saveProductivityTasksJson(encodeTasks(tasks.map { if (it.id == task.id) it.copy(done = checked) else it })) }
                        })
                        Text(task.title, Modifier.weight(1f), color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { scope.launch { store.saveProductivityTasksJson(encodeTasks(tasks.filterNot { it.id == task.id })) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir tarefa")
                        }
                    }
                }
            }
        }
        item {
            MoreSectionCard("Favoritos", "${favorites.size} notícias salvas neste aparelho") {
                if (favorites.isEmpty()) Text("Toque na estrela de uma notícia para guardá-la aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                favorites.forEach { article ->
                    Row(Modifier.fillMaxWidth().clickable { onArticleClick(article.url) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(article.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(article.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { scope.launch { store.toggleFavoriteArticle(article) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remover favorito")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        item {
            MoreSectionCard("Personalização", "Ajuste a aparência sem alterar seus dados.") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tema escuro", fontWeight = FontWeight.Medium)
                        Text("Fundo confortável para leitura noturna", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isDark, onCheckedChange = { networkRepo.setDarkThemeEnabled(it) })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Visual Neo", fontWeight = FontWeight.Medium)
                        Text("Cores neon e contraste mais intenso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isNeo, onCheckedChange = { networkRepo.setNeoThemeEnabled(it) })
                }
            }
        }
        item {
            MoreSectionCard("Saúde e bem-estar", "Os dados detalhados ficam na aba Saúde.") {
                Text("Acompanhe passos, sono, hidratação e indicadores disponíveis no aparelho. O Health Connect só é acessado após autorização.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            MoreSectionCard("Clima e notícias", "Dados atuais sem assinatura obrigatória.") {
                Text("A previsão e o feed ficam nas abas dedicadas. Use Atualizar para buscar dados recentes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MoreSectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

private fun decodeTasks(raw: String): List<LocalTask> = try {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val title = item.optString("title").trim()
        if (title.isBlank()) null else LocalTask(item.optString("id").ifBlank { UUID.randomUUID().toString() }, title, item.optBoolean("done", false))
    }
} catch (_: Exception) { emptyList() }

private fun encodeTasks(tasks: List<LocalTask>): String = JSONArray().apply {
    tasks.forEach { task -> put(JSONObject().apply { put("id", task.id); put("title", task.title); put("done", task.done) }) }
}.toString()
