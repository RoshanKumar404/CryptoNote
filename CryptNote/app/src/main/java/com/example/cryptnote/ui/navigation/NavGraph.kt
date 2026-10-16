package com.example.cryptnote.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cryptnote.ui.editor.NoteEditorScreen
import com.example.cryptnote.ui.editor.NoteEditorViewModel
import com.example.cryptnote.ui.notes.NotesListScreen
import com.example.cryptnote.ui.notes.NotesListViewModel

sealed class Screen(val route: String) {
    data object NotesList : Screen("notes_list")
    data object NoteEditor : Screen("note_editor/{noteId}") {
        fun createRoute(noteId: String) = "note_editor/$noteId"
    }
}

@Composable
fun CryptoNoteNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.NotesList.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(route = Screen.NotesList.route) {
            val viewModel: NotesListViewModel = hiltViewModel()
            NotesListScreen(
                viewModel = viewModel,
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                }
            )
        }

        composable(
            route = Screen.NoteEditor.route,
            arguments = listOf(
                navArgument("noteId") {
                    type = NavType.StringType
                    defaultValue = "new"
                }
            )
        ) {
            val viewModel: NoteEditorViewModel = hiltViewModel()
            NoteEditorScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
