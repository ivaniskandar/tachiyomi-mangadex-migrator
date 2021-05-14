package tachiyomi.source.model

data class MangasPageInfo(
  val mangas: List<MangaInfo>,
  val hasNextPage: Boolean
)
