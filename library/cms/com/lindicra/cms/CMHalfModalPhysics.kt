package com.lindicra.cms

/** 释放意图同时考虑距离与动量；快速向上甩动可打断已经下拉很远的关闭。 */
internal fun cmHalfModalShouldDismiss(offset: Float, height: Float, velocity: Float): Boolean =
    velocity > 900f || (velocity > -700f && offset + velocity * 0.16f > height * 0.30f)
