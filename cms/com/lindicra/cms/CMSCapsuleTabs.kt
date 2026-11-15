package com.lindicra.cms

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 旧入口与正式胶囊导航共用实现。 */
@Composable
@Deprecated("Use CMS.Navigation.CapsuleNavigation")
fun CMSCapsuleTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) = CMSCapsuleNavigation(labels, selectedIndex, onSelectedChange, modifier,
    if (outlined) CMSCapsuleVariant.Outlined else CMSCapsuleVariant.Filled)
