package com.awadytv.domain

/** A movie or series card as shown in grids and shelves; [caption] carries a recommendation reason. */
data class PosterItem(
    val kind: ContentKind,
    val id: Long,
    val title: String,
    val image: String?,
    val backdrop: String? = null,
    val badge: String? = null,
    val caption: String? = null,
    val progress: Float? = null,
)
