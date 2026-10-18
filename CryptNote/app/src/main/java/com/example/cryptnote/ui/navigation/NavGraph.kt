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
import com.example.cryptnote.ui.auth.AuthScreen
import com.example.cryptnote.ui.auth.AuthViewModel
import com.example.cryptnote.ui.auth.VaultAuthScreen
import com.example.cryptnote.ui.auth.VaultAuthViewModel
import com.example.cryptnote.ui.editor.NoteEditorScreen
import com.example.cryptnote.ui.editor.NoteEditorViewModel
import com.example.cryptnote.ui.notes.NotesListScreen
import com.example.cryptnote.ui.notes.NotesListViewModel

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object VaultAuth : Screen("vault_auth")
    data object NotesList : Screen("notes_list")
    data object NoteEditor : Screen("note_editor/{noteId}") {
        fun createRoute(noteId: String) = "note_editor/$noteId"
    }
}

@Composable
fun CryptoNoteNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Auth.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(route = Screen.Auth.route) {
            val viewModel: AuthViewModel = hiltViewModel()
            AuthScreen(
                viewModel = viewModel,
                onAuthenticated = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.VaultAuth.route) {
            val viewModel: VaultAuthViewModel = hiltViewModel()
            VaultAuthScreen(
                viewModel = viewModel,
                onAuthenticated = {
                    navController.navigate(Screen.NotesList.route) {
                        popUpTo(Screen.VaultAuth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(route = Screen.NotesList.route) {
            val viewModel: NotesListViewModel = hiltViewModel()
            NotesListScreen(
                viewModel = viewModel,
                onNavigateToEditor = { noteId ->
                    navController.navigate(Screen.NoteEditor.createRoute(noteId))
                },
                onLockVault = {
                    navController.navigate(Screen.VaultAuth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
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
