package com.customgeocache.app.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.customgeocache.app.ui.setup.steps.FolderStep
import com.customgeocache.app.ui.setup.steps.LoginStep
import com.customgeocache.app.ui.setup.steps.MapyKeyStep
import com.customgeocache.app.ui.setup.steps.WelcomeStep
import kotlinx.coroutines.launch

private const val STEP_COUNT = 4

@Composable
fun SetupWizardScreen(
    onFinish: () -> Unit,
    vm: SetupWizardViewModel = viewModel(factory = SetupWizardViewModel.Factory)
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pager = rememberPagerState(pageCount = { STEP_COUNT })
    val scope = rememberCoroutineScope()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) vm.onFolderSelected(context, uri)
    }

    Scaffold { padding ->
        Surface(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // progress bar nahoře
                LinearProgressIndicator(
                    progress = { (pager.currentPage + 1f) / STEP_COUNT },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = false
                ) { page ->
                    when (page) {
                        0 -> WelcomeStep()
                        1 -> FolderStep(
                            folderUri = state.folderUri,
                            onPickFolder = { folderPicker.launch(null) }
                        )
                        2 -> MapyKeyStep(
                            value = state.mapyApiKey,
                            onValueChange = vm::onMapyKeyChange
                        )
                        3 -> LoginStep(
                            state = state,
                            onUsernameChange = vm::onUsernameChange,
                            onPasswordChange = vm::onPasswordChange,
                            onLogin = vm::login
                        )
                    }
                }

                StepIndicator(currentPage = pager.currentPage, count = STEP_COUNT)
                Spacer(Modifier.height(8.dp))

                BottomBar(
                    currentPage = pager.currentPage,
                    canContinue = canContinue(pager.currentPage, state),
                    onBack = {
                        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                    },
                    onContinue = {
                        when (pager.currentPage) {
                            2 -> vm.saveMapyKey()
                        }
                        if (pager.currentPage < STEP_COUNT - 1) {
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        } else {
                            vm.finish()
                            onFinish()
                        }
                    }
                )
            }
        }
    }
}

private fun canContinue(page: Int, state: SetupUiState): Boolean = when (page) {
    0 -> true
    1 -> state.folderUri != null
    2 -> true   // klíč je nepovinný — necháme uživatele přeskočit a doplnit v Nastavení
    3 -> true   // login lze přeskočit (Skip = pokračovat bez sessionu)
    else -> true
}

@Composable
private fun StepIndicator(currentPage: Int, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val color = if (index <= currentPage)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 12.dp else 8.dp)
                    .padding(2.dp)
            ) {
                Surface(
                    color = color,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize()
                ) {}
            }
            if (index != count - 1) Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun BottomBar(
    currentPage: Int,
    canContinue: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = currentPage > 0) {
            TextButton(onClick = onBack) {
                Text(text = androidx.compose.ui.res.stringResource(com.customgeocache.app.R.string.setup_back))
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onContinue, enabled = canContinue) {
            Text(
                text = androidx.compose.ui.res.stringResource(
                    if (currentPage == STEP_COUNT - 1) com.customgeocache.app.R.string.setup_finish
                    else com.customgeocache.app.R.string.setup_continue
                )
            )
        }
    }
}

