package com.cutedino.xiangsuplayer.core.toplist

/**
 * 30个内置官方排行榜目录（完整移植自 XiangsuPlayerHQ ToplistCatalog.kt）：
 * - 网易云 (wy): 10 个核心榜单
 * - QQ音乐 (tx): 10 个核心榜单
 * - 酷狗音乐 (kg): 10 个核心榜单
 */
object ToplistCatalog {

    enum class Platform(val key: String, val label: String) {
        WY("wy", "网易云"),
        TX("tx", "QQ音乐"),
        KG("kg", "酷狗");

        companion object {
            fun fromKey(key: String): Platform =
                entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: WY
        }
    }

    data class Entry(
        val id: String,
        val name: String,
        val bangid: String,
        val platformKey: String,
        val subtitle: String = ""
    )

    val WY_BOARDS: List<Entry> = listOf(
        Entry("wy__bsb", "飙升榜", "19723756", "wy", "每日更新"),
        Entry("wy__xgb", "新歌榜", "3779629", "wy", "每日更新"),
        Entry("wy__ycb", "原创榜", "2884035", "wy", "每周四更新"),
        Entry("wy__rgb", "热歌榜", "3778678", "wy", "每周四更新"),
        Entry("wy__ayb", "云音乐说唱榜", "991319590", "wy", "每周五更新"),
        Entry("wy__gDB", "云音乐古典榜", "71384707", "wy", "每周四更新"),
        Entry("wy__dyb", "云音乐电音榜", "1978921795", "wy", "每周五更新"),
        Entry("wy__dyb2", "抖音排行榜", "2250011882", "wy", "每周更新"),
        Entry("wy__ACG", "云音乐ACG榜", "71385702", "wy", "每周四更新"),
        Entry("wy__oumei", "云音乐欧美热歌榜", "2809513713", "wy", "每周四更新")
    )

    val TX_BOARDS: List<Entry> = listOf(
        Entry("tx__rgb", "热歌榜", "26", "tx", "QQ音乐巅峰榜"),
        Entry("tx__xgb", "新歌榜", "27", "tx", "QQ音乐巅峰榜"),
        Entry("tx__bsb", "飙升榜", "62", "tx", "QQ音乐巅峰榜"),
        Entry("tx__lxb", "流行指数榜", "4", "tx", "QQ音乐巅峰榜"),
        Entry("tx__tgb", "听歌识曲榜", "67", "tx", "QQ音乐巅峰榜"),
        Entry("tx__ndb", "内地榜", "5", "tx", "地区巅峰榜"),
        Entry("tx__gtb", "港台榜", "59", "tx", "地区巅峰榜"),
        Entry("tx__omb", "欧美榜", "3", "tx", "地区巅峰榜"),
        Entry("tx__hgb", "韩国榜", "16", "tx", "地区巅峰榜"),
        Entry("tx__rbb", "日本榜", "17", "tx", "地区巅峰榜")
    )

    val KG_BOARDS: List<Entry> = listOf(
        Entry("kg__52144", "酷狗飙升榜", "6666", "kg", "酷狗官方榜"),
        Entry("kg__8888", "酷狗TOP500", "8888", "kg", "酷狗官方榜"),
        Entry("kg__59703", "蜂鸟流行音乐榜", "59703", "kg", "流行风向"),
        Entry("kg__52767", "抖音热歌榜", "52767", "kg", "全网热播"),
        Entry("kg__24971", "DJ热歌榜", "24971", "kg", "动感电音"),
        Entry("kg__31308", "华语新歌榜", "31308", "kg", "华语新声"),
        Entry("kg__31310", "欧美新歌榜", "31310", "kg", "欧美精选"),
        Entry("kg__31311", "韩国新歌榜", "31311", "kg", "K-Pop热曲"),
        Entry("kg__31312", "日本新歌榜", "31312", "kg", "J-Pop精选"),
        Entry("kg__33162", "ACG新歌榜", "33162", "kg", "二次元精选")
    )

    fun boardsFor(platform: Platform): List<Entry> = when (platform) {
        Platform.WY -> WY_BOARDS
        Platform.TX -> TX_BOARDS
        Platform.KG -> KG_BOARDS
    }
}
