package com.vortexsu.vortexsu.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.system.Os
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuSFSConfigScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.vortexsu.vortexsu.KernelVersion
import com.vortexsu.vortexsu.Natives
import com.vortexsu.vortexsu.R
import com.vortexsu.vortexsu.ui.component.KsuIsValid
import com.vortexsu.vortexsu.ui.component.rememberConfirmDialog
import com.vortexsu.vortexsu.ui.theme.CardConfig
import com.vortexsu.vortexsu.ui.theme.CardConfig.cardAlpha
import com.vortexsu.vortexsu.ui.theme.getCardColors
import com.vortexsu.vortexsu.ui.theme.getCardElevation
import com.vortexsu.vortexsu.ui.susfs.util.SuSFSManager
import com.vortexsu.vortexsu.ui.util.checkNewVersion
import com.vortexsu.vortexsu.ui.util.getSuSFSStatus
import com.vortexsu.vortexsu.ui.util.module.LatestVersionInfo
import com.vortexsu.vortexsu.ui.util.reboot
import com.vortexsu.vortexsu.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * @author kingfinik98
 * @date 2026/2/28.
 * UI Style Refactor: Liquid Glass Modern UI
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Destination<RootGraph>(start = true)
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val viewModel = viewModel<HomeViewModel>()
    val coroutineScope = rememberCoroutineScope()

    val pullRefreshState = rememberPullRefreshState(
        refreshing = viewModel.isRefreshing,
        onRefresh = {
            viewModel.onPullRefresh(context)
        }
    )

    LaunchedEffect(key1 = navigator) {
        viewModel.loadUserSettings(context)
        coroutineScope.launch {
            viewModel.loadCoreData()
            delay(100)
            viewModel.loadExtendedData(context)
        }

        coroutineScope.launch {
            while (true) {
                delay(5000)
                viewModel.autoRefreshIfNeeded(context)
            }
        }
    }

    LaunchedEffect(viewModel.dataRefreshTrigger) {
        viewModel.dataRefreshTrigger.collect { _ -> }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopBar(
                scrollBehavior = scrollBehavior,
                navigator = navigator,
                isDataLoaded = viewModel.isCoreDataLoaded,
                customBannerUri = viewModel.customBannerUri
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        top = 12.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status Cards (Split Design)
                if (viewModel.isCoreDataLoaded) {
                    LiquidGlassStatusSection(
                        systemStatus = viewModel.systemStatus,
                        onClickInstall = {
                            navigator.navigate(InstallScreenDestination(preselectedKernelUri = null))
                        }
                    )

                    // Warnings
                    if (viewModel.systemStatus.requireNewKernel) {
                        WarningCard(
                            stringResource(id = R.string.require_kernel_version).format(
                                Natives.getSimpleVersionFull(),
                                Natives.MINIMAL_SUPPORTED_KERNEL_FULL
                            )
                        )
                    }

                    if (viewModel.systemStatus.ksuVersion != null && !viewModel.systemStatus.isRootAvailable) {
                        WarningCard(stringResource(id = R.string.grant_root_failed))
                    }

                    val shouldShowWarnings = viewModel.systemStatus.requireNewKernel ||
                            (viewModel.systemStatus.ksuVersion != null && !viewModel.systemStatus.isRootAvailable)

                    if (Natives.version <= Natives.MINIMAL_NEW_IOCTL_KERNEL && !shouldShowWarnings && viewModel.systemStatus.ksuVersion != null) {
                        IncompatibleKernelCard()
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Extended Data
                if (viewModel.isExtendedDataLoaded) {
                    val checkUpdate = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getBoolean("check_update", true)
                    if (checkUpdate) {
                        UpdateCard()
                    }

                    // Specs Card (Grid/Flow Design)
                    LiquidGlassSpecsCard(
                        systemInfo = viewModel.systemInfo,
                        isSimpleMode = viewModel.isSimpleMode,
                        isHideSusfsStatus = viewModel.isHideSusfsStatus,
                        isHideZygiskImplement = viewModel.isHideZygiskImplement,
                        isHideMetaModuleImplement = viewModel.isHideMetaModuleImplement,
                        showKpmInfo = viewModel.showKpmInfo,
                        lkmMode = viewModel.systemStatus.lkmMode,
                    )

                    // Link Cards
                    if (!viewModel.isSimpleMode && !viewModel.isHideLinkCard) {
                        ContributionCard()
                        DonateCard()
                        LearnMoreCard()
                    }
                }

                if (!viewModel.isExtendedDataLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ================= LIQUID GLASS COMPONENTS =================

/**
 * Modifier helper for Liquid Glass Effect
 */
fun Modifier.liquidGlassBackground(
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    baseColor: Color = Color.White.copy(alpha = 0.1f)
): Modifier = this
    .clip(shape)
    .background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    )
    .background(baseColor) // Base tint
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))
        ),
        shape = shape
    )

@Composable
private fun LiquidGlassStatusSection(
    systemStatus: HomeViewModel.SystemStatus,
    onClickInstall: () -> Unit
) {
    val isWorking = systemStatus.ksuVersion != null
    val isLkm = systemStatus.lkmMode == true

    val successColor = MaterialTheme.colorScheme.primary
    val warningColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    // Container for the split cards
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Status (Working / Unsupported)
        Box(
            modifier = Modifier
                .weight(1f)
                .liquidGlassBackground(
                    shape = RoundedCornerShape(20.dp),
                    baseColor = if (isWorking) successColor.copy(alpha = 0.1f) else errorColor.copy(alpha = 0.1f)
                )
                .clickable(enabled = systemStatus.isRootAvailable || systemStatus.kernelVersion.isGKI()) {
                    onClickInstall()
                }
                .padding(vertical = 20.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isWorking) Icons.Outlined.TaskAlt else Icons.Outlined.Block,
                    contentDescription = null,
                    tint = if (isWorking) successColor else errorColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (isWorking) "Working" else "Unsupported",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isWorking) successColor else errorColor
                )
            }
        }

        // Card 2: Mode (LKM / GKI)
        Box(
            modifier = Modifier
                .weight(1f)
                .liquidGlassBackground(
                    shape = RoundedCornerShape(20.dp),
                    baseColor = warningColor.copy(alpha = 0.1f)
                )
                .padding(vertical = 20.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isLkm) Icons.Default.Memory else Icons.Default.Android,
                    contentDescription = null,
                    tint = warningColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (isLkm) "LKM Mode" else "GKI",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = warningColor
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiquidGlassSpecsCard(
    systemInfo: HomeViewModel.SystemInfo,
    isSimpleMode: Boolean,
    isHideSusfsStatus: Boolean,
    isHideZygiskImplement: Boolean,
    isHideMetaModuleImplement: Boolean,
    showKpmInfo: Boolean,
    lkmMode: Boolean?
) {
    val context = LocalContext.current

    // Main Container Card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassBackground(shape = RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "SYSTEM SPECS",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // FlowRow for Grid-like layout
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpecChip(icon = Icons.Default.Memory, label = "Kernel", value = systemInfo.kernelRelease)
            
            if (!isSimpleMode) {
                SpecChip(icon = Icons.Default.Android, label = "Android", value = systemInfo.androidVersion)
            }

            SpecChip(icon = Icons.Default.PhoneAndroid, label = "Device", value = systemInfo.deviceModel)

            val isSpoofed = context.packageName != "com.vortexsu.vortexsu"
            val versionDisplay = "${systemInfo.managerVersion.first}" + 
                                 if (isSpoofed) " (Spoofed)" else ""
            
            SpecChip(icon = Icons.Default.SettingsSuggest, label = "Manager", value = versionDisplay)

            SpecChip(
                icon = Icons.Default.Security, 
                label = "SELinux", 
                value = systemInfo.seLinuxStatus,
                valueColor = if (systemInfo.seLinuxStatus.equals("Enforcing", true)) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error
            )

            if (!isSimpleMode && !isHideZygiskImplement && systemInfo.zygiskImplement != "None") {
                SpecChip(icon = Icons.Default.Adb, label = "Zygisk", value = systemInfo.zygiskImplement)
            }

            if (!isSimpleMode && !isHideMetaModuleImplement && systemInfo.metaModuleImplement != "None") {
                SpecChip(icon = Icons.Default.Extension, label = "Module", value = systemInfo.metaModuleImplement)
            }

            if (!isSimpleMode && !isHideSusfsStatus && systemInfo.suSFSStatus == "Supported" && systemInfo.suSFSVersion.isNotEmpty()) {
                val infoText = "${systemInfo.suSFSVersion} (${Natives.getHookType()})"
                SpecChip(icon = Icons.Default.Storage, label = "SuSFS", value = infoText)
            }
        }
    }
}

@Composable
fun SpecChip(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = Modifier.defaultMinSize(minHeight = 42.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f), // Semi-transparent inner card
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Light),
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ================= STANDARD COMPONENTS (Kept for reference/usage) =================

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val latestVersionInfo = LatestVersionInfo()
    val newVersion by produceState(initialValue = latestVersionInfo) {
        value = withContext(Dispatchers.IO) {
            checkNewVersion()
        }
    }

    val currentVersionCode = getManagerVersion(context).second
    val newVersionCode = newVersion.versionCode
    val newVersionUrl = newVersion.downloadUrl
    val changelog = newVersion.changelog

    val uriHandler = LocalUriHandler.current
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = newVersionCode > currentVersionCode,
        enter = fadeIn() + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { uriHandler.openUri(newVersionUrl) })
        WarningCard(
            message = stringResource(id = R.string.new_version_available).format(newVersionCode),
            color = MaterialTheme.colorScheme.outlineVariant,
            onClick = {
                if (changelog.isEmpty()) {
                    uriHandler.openUri(newVersionUrl)
                } else {
                    updateDialog.showConfirm(
                        title = title,
                        content = changelog,
                        markdown = true,
                        confirm = updateText
                    )
                }
            }
        )
    }
}

@Composable
fun RebootDropdownItem(@StringRes id: Int, reason: String = "") {
    DropdownMenuItem(
        text = { Text(stringResource(id)) },
        onClick = { reboot(reason) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
    navigator: DestinationsNavigator,
    isDataLoaded: Boolean = false,
    customBannerUri: Uri? = null
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(24.dp),
            colors = getCardColors(colorScheme.surfaceContainerHigh),
            elevation = getCardElevation()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background Image
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(customBannerUri ?: R.drawable.header_bg)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                    contentScale = ContentScale.Crop
                )
                
                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                                    colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Content
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "VorteXSU",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = colorScheme.primary
                            )
                            Text(
                                text = "完全なルート制御",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDataLoaded) {
                                if (getSuSFSStatus().equals("true", ignoreCase = true) && SuSFSManager.isBinaryAvailable(context)) {
                                    IconButton(onClick = {
                                        navigator.navigate(SuSFSConfigScreenDestination)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Tune,
                                            contentDescription = stringResource(R.string.susfs_config_setting_title),
                                            tint = colorScheme.primary
                                        )
                                    }
                                }

                                var showDropdown by remember { mutableStateOf(false) }
                                KsuIsValid {
                                    IconButton(onClick = {
                                        showDropdown = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.PowerSettingsNew,
                                            contentDescription = stringResource(id = R.string.reboot),
                                            tint = colorScheme.primary
                                        )

                                        DropdownMenu(expanded = showDropdown, onDismissRequest = {
                                            showDropdown = false
                                        }) {
                                            RebootDropdownItem(id = R.string.reboot)
                                            val pm =
                                                LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?
                                            @Suppress("DEPRECATION")
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true) {
                                                RebootDropdownItem(id = R.string.reboot_userspace, reason = "userspace")
                                            }
                                            RebootDropdownItem(id = R.string.reboot_recovery, reason = "recovery")
                                            RebootDropdownItem(id = R.string.reboot_bootloader, reason = "bootloader")
                                            RebootDropdownItem(id = R.string.reboot_download, reason = "download")
                                            RebootDropdownItem(id = R.string.reboot_edl, reason = "edl")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WarningCard(
    message: String,
    color: Color = MaterialTheme.colorScheme.error,
    onClick: (() -> Unit)? = null
) {
    ElevatedCard(
        colors = getCardColors(color),
        elevation = getCardElevation(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun ContributionCard() {
    val uriHandler = LocalUriHandler.current
    val links = listOf("https://github.com/ShirkNeko", "https://github.com/udochina")

    ElevatedCard(
        colors = getCardColors(MaterialTheme.colorScheme.surfaceContainer),
        elevation = getCardElevation(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val randomIndex = Random.nextInt(links.size)
                    uriHandler.openUri(links[randomIndex])
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_ContributionCard_kernelsu),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_click_to_ContributionCard_kernelsu),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun LearnMoreCard() {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.home_learn_kernelsu_url)

    ElevatedCard(
        colors = getCardColors(MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri(url)
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_learn_kernelsu),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_click_to_learn_kernelsu),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun DonateCard() {
    val uriHandler = LocalUriHandler.current

    ElevatedCard(
        colors = getCardColors(MaterialTheme.colorScheme.surfaceContainer),
        elevation = getCardElevation(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri("https://patreon.com/weishu")
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_support_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_support_content),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun IncompatibleKernelCard() {
    val currentKver = remember { Natives.version }
    val threshold = Natives.MINIMAL_NEW_IOCTL_KERNEL

    val msg = stringResource(
        id = R.string.incompatible_kernel_msg,
        currentKver,
        threshold
    )

    WarningCard(
        message = msg,
        color = MaterialTheme.colorScheme.error
    )
}

fun getManagerVersion(context: Context): Pair<String, Long> {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)!!
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    return Pair(packageInfo.versionName!!, versionCode)
}

@Preview
@Composable
private fun StatusCardPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LiquidGlassStatusSection(
            HomeViewModel.SystemStatus(
                isManager = true,
                ksuVersion = 1,
                lkmMode = true,
                kernelVersion = KernelVersion(5, 10, 101),
                isRootAvailable = true
            )
        )
        LiquidGlassStatusSection(
            HomeViewModel.SystemStatus(
                isManager = true,
                ksuVersion = 1,
                lkmMode = false, // GKI Mode
                kernelVersion = KernelVersion(5, 10, 101),
                isRootAvailable = true
            )
        )
    }
}

@Preview
@Composable
private fun WarningCardPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WarningCard(message = "Warning message")
        WarningCard(
            message = "Warning message ",
            MaterialTheme.colorScheme.outlineVariant,
            onClick = {})
    }
}
