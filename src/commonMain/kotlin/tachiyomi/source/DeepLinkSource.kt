package tachiyomi.source

interface DeepLinkSource : Source {

  fun handleLink(url: String): DeepLink?

  fun findMangaKey(chapterKey: String): String?

}
